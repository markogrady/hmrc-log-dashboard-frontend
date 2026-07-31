package repositories

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.bson.BsonNull
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import models.{LogEntry, LogSeverity}

object LogEntryRepository {

  /** Mongo format: timestamps as BSON dates (index-friendly range queries), enums as strings, UUIDs as strings. */
  val mongoFormat: OFormat[LogEntry] = {
    implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    import models.EnumFormats._
    Json.format[LogEntry]
  }
}

@Singleton
class LogEntryRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[LogEntry](
      collectionName = "log-entries",
      mongoComponent = mongoComponent,
      domainFormat = LogEntryRepository.mongoFormat,
      indexes = Seq(
        IndexModel(Indexes.ascending("applicationId", "timestampUtc"), IndexOptions().name("appId_timestamp")),
        IndexModel(
          Indexes.ascending("applicationId", "endpointKey", "timestampUtc"),
          IndexOptions().name("appId_endpointKey_timestamp")
        ),
        IndexModel(Indexes.ascending("correlationId"), IndexOptions().name("correlationId")),
        IndexModel(Indexes.ascending("applicationId", "clientId"), IndexOptions().name("appId_clientId"))
      )
    ) {

  // Log entries are the dataset under review; they must never expire.
  override lazy val requiresTtlIndex: Boolean = false

  def insertMany(entries: Seq[LogEntry]): Future[Unit] =
    if (entries.isEmpty) Future.unit
    else
      collection
        .insertMany(entries, InsertManyOptions().ordered(false))
        .toFuture()
        .map(_ => ())

  /** All lines for one application within [monthStart, monthEnd) that carry a correlationId and endpointKey. */
  def findMonthSlice(
    appId: UUID,
    monthStart: Instant,
    monthEnd: Instant,
    clientId: Option[String]
  ): Future[Seq[LogEntry]] =
    collection.find(monthSliceFilter(appId, monthStart, monthEnd, clientId)).toFuture()

  /** Month slice for a single endpoint (endpoint detail page). */
  def findEndpointMonthSlice(
    appId: UUID,
    endpointKey: String,
    monthStart: Instant,
    monthEnd: Instant,
    clientId: Option[String]
  ): Future[Seq[LogEntry]] = {
    val filters = Seq(
      Filters.equal("applicationId", appId.toString),
      Filters.equal("endpointKey", endpointKey),
      Filters.gte("timestampUtc", monthStart),
      Filters.lt("timestampUtc", monthEnd),
      Filters.ne("correlationId", BsonNull.VALUE)
    ) ++ clientId.map(c => Filters.equal("clientId", c))
    collection.find(Filters.and(filters *)).toFuture()
  }

  def findByCorrelationId(correlationId: String): Future[Seq[LogEntry]] =
    collection
      .find(Filters.equal("correlationId", correlationId))
      .sort(Sorts.ascending("timestampUtc"))
      .toFuture()

  def findByCorrelationIds(correlationIds: Seq[String]): Future[Seq[LogEntry]] =
    if (correlationIds.isEmpty) Future.successful(Seq.empty)
    else
      collection
        .find(Filters.in("correlationId", correlationIds *))
        .sort(Sorts.ascending("timestampUtc"))
        .toFuture()

  def distinctClients(appId: UUID): Future[Seq[String]] =
    collection
      .distinct[String]("clientId", Filters.equal("applicationId", appId.toString))
      .toFuture()
      .map(_.sorted)

  /** Distinct (year, month) pairs present for an application. */
  def distinctMonths(appId: UUID): Future[Seq[(Int, Int)]] =
    collection
      .aggregate[BsonDocument](
        Seq(
          Aggregates.`match`(Filters.equal("applicationId", appId.toString)),
          Aggregates.group(
            BsonDocument("year" -> BsonDocument("$year" -> "$timestampUtc"), "month" -> BsonDocument("$month" -> "$timestampUtc"))
          )
        )
      )
      .toFuture()
      .map(_.map { doc =>
        val id = doc.getDocument("_id")
        (id.getInt32("year").getValue, id.getInt32("month").getValue)
      })

  def lastTimestamp(appId: UUID): Future[Option[Instant]] =
    collection
      .find(Filters.equal("applicationId", appId.toString))
      .sort(Sorts.descending("timestampUtc"))
      .limit(1)
      .headOption()
      .map(_.map(_.timestampUtc))

  /** Warn-level lines for one application within the month window; strict clientId matching. */
  def findWarnings(
    appId: UUID,
    monthStart: Instant,
    monthEnd: Instant,
    clientId: Option[String]
  ): Future[Seq[LogEntry]] = {
    val filters = Seq(
      Filters.equal("applicationId", appId.toString),
      Filters.equal("level", LogSeverity.Warn.toString),
      Filters.gte("timestampUtc", monthStart),
      Filters.lt("timestampUtc", monthEnd)
    ) ++ clientId.map(c => Filters.equal("clientId", c))
    collection.find(Filters.and(filters *)).sort(Sorts.ascending("timestampUtc")).toFuture()
  }

  def count(): Future[Long] =
    collection.countDocuments().toFuture()

  def deleteAll(): Future[Unit] =
    collection.deleteMany(Filters.empty()).toFuture().map(_ => ())

  private def monthSliceFilter(
    appId: UUID,
    monthStart: Instant,
    monthEnd: Instant,
    clientId: Option[String]
  ) = {
    val filters = Seq(
      Filters.equal("applicationId", appId.toString),
      Filters.gte("timestampUtc", monthStart),
      Filters.lt("timestampUtc", monthEnd),
      Filters.ne("correlationId", BsonNull.VALUE),
      Filters.ne("endpointKey", BsonNull.VALUE)
    ) ++ clientId.map(c => Filters.equal("clientId", c))
    Filters.and(filters *)
  }
}
