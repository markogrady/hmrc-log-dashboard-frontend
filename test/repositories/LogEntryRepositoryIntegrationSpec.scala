package repositories

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

import models.{LogEntry, LogEventKind, LogSeverity}

class LogEntryRepositoryIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with DefaultPlayMongoRepositorySupport[LogEntry] {

  override protected val repository: LogEntryRepository = new LogEntryRepository(mongoComponent)

  private val appId = UUID.fromString("8dab5c01-ea7a-45c0-ae6a-c008edc1498e")
  private val june  = Instant.parse("2026-06-10T09:00:00Z")

  private def entry(
    correlationId: String,
    timestamp: Instant = june,
    clientId: Option[String] = Some("CUST-000001"),
    statusCode: Option[Int] = None,
    kind: LogEventKind = LogEventKind.Start,
    level: LogSeverity = LogSeverity.Info
  ): LogEntry =
    LogEntry(
      rawLine = s"raw $correlationId",
      timestampUtc = timestamp,
      level = level,
      applicationId = Some(appId),
      clientId = clientId,
      correlationId = Some(correlationId),
      endpointKey = Some("ListBusinessesController.listBusinesses"),
      apiArea = Some("Business Details"),
      kind = kind,
      statusCode = statusCode,
      message = s"message $correlationId",
      parsed = true
    )

  "LogEntryRepository" should {

    "insert batches and count them" in {
      repository.insertMany(Seq(entry("c1"), entry("c2"))).futureValue
      repository.count().futureValue shouldBe 2L
    }

    "round-trip a full entry through the Mongo format" in {
      val original = entry("c1", statusCode = Some(404), kind = LogEventKind.Failure, level = LogSeverity.Warn)
      repository.insertMany(Seq(original)).futureValue

      val loaded = repository.findByCorrelationId("c1").futureValue
      loaded should have size 1
      loaded.head shouldBe original
    }

    "return a month slice restricted to correlationId+endpointKey lines and optional client" in {
      val monthStart = Instant.parse("2026-06-01T00:00:00Z")
      val monthEnd   = Instant.parse("2026-07-01T00:00:00Z")

      repository
        .insertMany(
          Seq(
            entry("in-month"),
            entry("other-client", clientId = Some("CUST-000002")),
            entry("out-of-month", timestamp = Instant.parse("2026-05-10T09:00:00Z")),
            entry("no-endpoint").copy(endpointKey = None)
          )
        )
        .futureValue

      val all = repository.findMonthSlice(appId, monthStart, monthEnd, None).futureValue
      all.map(_.correlationId.get) should contain theSameElementsAs Seq("in-month", "other-client")

      val filtered = repository.findMonthSlice(appId, monthStart, monthEnd, Some("CUST-000002")).futureValue
      filtered.map(_.correlationId.get) shouldBe Seq("other-client")
    }

    "list distinct clients and months" in {
      repository
        .insertMany(
          Seq(
            entry("a", clientId = Some("CUST-B")),
            entry("b", clientId = Some("CUST-A")),
            entry("c", timestamp = Instant.parse("2026-05-01T12:00:00Z"))
          )
        )
        .futureValue

      repository.distinctClients(appId).futureValue shouldBe Seq("CUST-A", "CUST-B", "CUST-000001")
        .sorted
      repository.distinctMonths(appId).futureValue should contain theSameElementsAs Seq((2026, 6), (2026, 5))
    }

    "return the unfiltered last timestamp" in {
      val later = june.plus(3, ChronoUnit.DAYS)
      repository.insertMany(Seq(entry("a"), entry("b", timestamp = later))).futureValue
      repository.lastTimestamp(appId).futureValue shouldBe Some(later)
    }

    "find warnings with strict client matching" in {
      val monthStart = Instant.parse("2026-06-01T00:00:00Z")
      val monthEnd   = Instant.parse("2026-07-01T00:00:00Z")

      repository
        .insertMany(
          Seq(
            entry("w1", level = LogSeverity.Warn),
            entry("w2", level = LogSeverity.Warn, clientId = None),
            entry("i1", level = LogSeverity.Info)
          )
        )
        .futureValue

      repository.findWarnings(appId, monthStart, monthEnd, None).futureValue.map(_.correlationId.get) should
        contain theSameElementsAs Seq("w1", "w2")
      // strict clientId matching: warning lines without a clientId are excluded under a client filter
      repository
        .findWarnings(appId, monthStart, monthEnd, Some("CUST-000001"))
        .futureValue
        .map(_.correlationId.get) shouldBe Seq("w1")
    }
  }
}
