package services

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.Singleton
import scala.util.Random

import models.{EndpointDefinition, ItsaEndpointCatalog}

final case class SampleApplication(
  id: UUID,
  name: String,
  environment: String,
  softwareVersion: String,
  clientCount: Int,
  volumeShare: Double
)

/**
 * Deterministic generator of raw log lines in the adopted hmrc/vat-api format, fed through
 * the real parser/ingestion pipeline so seeding exercises the same code path as an import.
 *
 * Note: the seed matches the original .NET app (20260717) but java.util.Random and .NET
 * Random differ, so the data is distribution-equivalent rather than byte-identical.
 */
@Singleton
class SampleLogGenerator {

  import SampleLogGenerator._

  /** Generates raw log lines (two per request: start + outcome), ordered by timestamp. */
  def generate(totalRequests: Int = 5000, nowUtc: Option[Instant] = None): Seq[String] = {
    val random      = new Random(RandomSeed)
    val anchor      = LocalDateTime.ofInstant(nowUtc.getOrElse(Instant.now()), ZoneOffset.UTC)
    val windowStart = anchor.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).minusMonths(5)
    val windowSeconds = ChronoUnit.SECONDS.between(windowStart, anchor).toDouble

    val endpoints = endpointWeights.map { case (key, weight) =>
      val definition = ItsaEndpointCatalog
        .findByKey(key)
        .getOrElse(throw new IllegalStateException(s"Endpoint $key missing from catalog"))
      (definition, weight)
    }
    val endpointTotalWeight = endpoints.map(_._2).sum

    val lines = Vector.newBuilder[(LocalDateTime, String)]

    applications.foreach { app =>
      val clients = (1 to app.clientCount).map(_ => (s"CUST-${randomDigits(random, 6)}", randomNino(random)))

      val requests = (totalRequests * app.volumeShare).toInt
      (0 until requests).foreach { _ =>
        val (definition, _) = pickWeighted(random, endpoints, endpointTotalWeight)(_._2)
        val client          = clients(random.nextInt(clients.length))
        val timestamp       = randomBusinessSkewedTimestamp(random, windowStart, windowSeconds)
        val correlationId   = randomGuid(random)
        val requestId       = randomGuid(random)
        val thread          = s"application-pekko.actor.default-dispatcher-${random.between(1, 24)}"
        val outcome         = pickOutcome(random, definition.key)

        lines += timestamp -> formatLine(
          timestamp,
          "INFO",
          definition,
          thread,
          requestId,
          app.id,
          client._1,
          s"[${definition.controller}][${definition.endpointName}] ${definition.displayName} for NINO : ${client._2} with correlationId : $correlationId"
        )

        val outcomeTime = timestamp.plus(random.between(180, 1600).toLong, ChronoUnit.MILLIS)
        if (outcome.status < 400) {
          lines += outcomeTime -> formatLine(
            outcomeTime,
            "INFO",
            definition,
            thread,
            requestId,
            app.id,
            client._1,
            s"[${definition.controller}][${definition.endpointName}] Success response received with correlationId : $correlationId"
          )
        } else {
          val level = if (outcome.status >= 500) "ERROR" else "WARN"
          val code  = outcome.code.getOrElse("")
          val body  = s"""{"code":"$code","message":"${errorMessages(code)}"}"""
          lines += outcomeTime -> formatLine(
            outcomeTime,
            level,
            definition,
            thread,
            requestId,
            app.id,
            client._1,
            s"[${definition.controller}][${definition.endpointName}] Error response received with status: ${outcome.status} and body: $body with correlationId : $correlationId"
          )
        }
      }
    }

    lines.result().sortBy(_._1).map(_._2)
  }

  private def formatLine(
    timestamp: LocalDateTime,
    level: String,
    definition: EndpointDefinition,
    thread: String,
    requestId: String,
    appId: UUID,
    clientId: String,
    message: String
  ): String =
    s"${timestamp.format(TimestampFormat)} level=[$level] logger=[v3.controllers.${definition.controller}] " +
      s"thread=[$thread] rid=[$requestId] appId=[$appId] clientId=[$clientId] message=[$message]"

  private def pickOutcome(random: Random, endpointKey: String): Outcome = {
    val outcomes = outcomeOverrides.getOrElse(endpointKey, defaultOutcomes)
    pickWeighted(random, outcomes, outcomes.map(_.weight).sum)(_.weight)
  }

  private def pickWeighted[T](random: Random, items: Seq[T], totalWeight: Int)(weight: T => Int): T = {
    var roll = random.nextInt(totalWeight)
    items.find { item =>
      roll -= weight(item)
      roll < 0
    }.getOrElse(items.last)
  }

  private def randomBusinessSkewedTimestamp(
    random: Random,
    windowStart: LocalDateTime,
    windowSeconds: Double
  ): LocalDateTime = {
    val moment = windowStart.plusSeconds((random.nextDouble() * windowSeconds).toLong)
    // Pull most traffic into 08:00-19:00 UK office hours.
    val hour = if (random.nextInt(10) < 8) random.between(8, 19) else random.between(0, 24)
    LocalDateTime.of(
      moment.getYear,
      moment.getMonth,
      moment.getDayOfMonth,
      hour,
      random.nextInt(60),
      random.nextInt(60),
      random.nextInt(1000) * 1_000_000
    )
  }

  private def randomNino(random: Random): String =
    s"${('A' + random.nextInt(20)).toChar}${('A' + random.nextInt(20)).toChar}${random.between(100000, 999999)}${('A' + random.nextInt(4)).toChar}"

  private def randomDigits(random: Random, count: Int): String =
    (1 to count).map(_ => ('0' + random.nextInt(10)).toChar).mkString

  private def randomGuid(random: Random): String = {
    val bytes = new Array[Byte](16)
    random.nextBytes(bytes)
    UUID.nameUUIDFromBytes(bytes).toString
  }
}

object SampleLogGenerator {

  private val RandomSeed = 20260717

  private val TimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS")

  val applications: Seq[SampleApplication] = Seq(
    SampleApplication(
      UUID.fromString("8dab5c01-ea7a-45c0-ae6a-c008edc1498e"),
      "Tax Optimiser",
      "Production",
      "taxoptimiser=v2021.1",
      clientCount = 12,
      volumeShare = 0.60
    ),
    SampleApplication(
      UUID.fromString("3f1c2a77-5b2e-4d0a-9c1f-6e8b1c9a4d21"),
      "Tax Optimiser (Sandbox)",
      "Sandbox",
      "taxoptimiser=v2021.1",
      clientCount = 4,
      volumeShare = 0.12
    ),
    SampleApplication(
      UUID.fromString("b6f0a9d4-1c3e-4f7b-8a2d-5e9c0b7f6a38"),
      "BooksFlow Accounting",
      "Production",
      "booksflow=v4.2.0",
      clientCount = 8,
      volumeShare = 0.28
    )
  )

  private final case class Outcome(status: Int, code: Option[String], weight: Int)

  // (endpoint key, relative traffic weight). Weighted towards the calls a filing app makes constantly.
  private val endpointWeights: Seq[(String, Int)] = Seq(
    "RetrieveItsaObligationsController.retrieveItsaObligations"                                 -> 14,
    "ListBusinessesController.listBusinesses"                                                   -> 11,
    "RetrieveBusinessDetailsController.retrieveBusinessDetails"                                 -> 9,
    "ListCalculationsController.listCalculations"                                               -> 8,
    "RetrieveCalculationController.retrieveCalculation"                                         -> 8,
    "TriggerCalculationController.triggerCalculation"                                           -> 6,
    "RetrieveItsaStatusController.retrieveItsaStatus"                                           -> 6,
    "RetrieveSelfEmploymentCumulativeSummaryController.retrieveSelfEmploymentCumulativeSummary" -> 5,
    "AmendSelfEmploymentCumulativeSummaryController.amendSelfEmploymentCumulativeSummary"       -> 5,
    "RetrieveUkPropertyCumulativeSummaryController.retrieveUkPropertyCumulativeSummary"         -> 4,
    "AmendUkPropertyCumulativeSummaryController.amendUkPropertyCumulativeSummary"               -> 4,
    "ListEmploymentsController.listEmployments"                                                 -> 4,
    "RetrieveEmploymentController.retrieveEmployment"                                           -> 3,
    "RetrieveFinancialDetailsController.retrieveFinancialDetails"                               -> 3,
    "AmendFinancialDetailsController.amendFinancialDetails"                                     -> 3,
    "RetrieveDividendsController.retrieveDividends"                                             -> 3,
    "AmendDividendsController.amendDividends"                                                   -> 2,
    "RetrieveOtherSavingsController.retrieveOtherSavings"                                       -> 2,
    "AmendOtherSavingsController.amendOtherSavings"                                             -> 2,
    "RetrievePensionsReliefsController.retrievePensionsReliefs"                                 -> 2,
    "AmendPensionsReliefsController.amendPensionsReliefs"                                       -> 2,
    "RetrieveCharitableGivingReliefController.retrieveCharitableGivingRelief"                   -> 2,
    "ListBFLossesController.listBroughtForwardLosses"                                           -> 2,
    "CreateBFLossController.createBroughtForwardLoss"                                           -> 1,
    "RetrieveCisDeductionsController.retrieveCisDeductions"                                     -> 2,
    "ListBsasController.listBsas"                                                               -> 2,
    "TriggerBsasController.triggerBsas"                                                         -> 1,
    "SubmitFinalDeclarationController.submitFinalDeclaration"                                   -> 3,
    "RetrieveSelfEmploymentAnnualSubmissionController.retrieveSelfEmploymentAnnualSubmission"   -> 2,
    "AmendSelfEmploymentAnnualSubmissionController.amendSelfEmploymentAnnualSubmission"         -> 2
  )

  // outcome mix per endpoint key: (status, error code, weight). Default applied when absent.
  private val defaultOutcomes: Seq[Outcome] = Seq(
    Outcome(200, None, 96),
    Outcome(404, Some("MATCHING_RESOURCE_NOT_FOUND"), 3),
    Outcome(400, Some("FORMAT_NINO"), 1)
  )

  private val outcomeOverrides: Map[String, Seq[Outcome]] = Map(
    // deliberately unhealthy: 5xx present => red "Fix" tag
    "SubmitFinalDeclarationController.submitFinalDeclaration" -> Seq(
      Outcome(200, None, 70),
      Outcome(403, Some("CLIENT_OR_AGENT_NOT_AUTHORISED"), 15),
      Outcome(500, Some("INTERNAL_SERVER_ERROR"), 10),
      Outcome(400, Some("RULE_TAX_YEAR_NOT_SUPPORTED"), 5)
    ),
    // deliberately flaky 4xx-heavy => red by failure rate
    "AmendFinancialDetailsController.amendFinancialDetails" -> Seq(
      Outcome(200, None, 62),
      Outcome(404, Some("MATCHING_RESOURCE_NOT_FOUND"), 28),
      Outcome(400, Some("FORMAT_NINO"), 10)
    ),
    // mild 4xx => yellow "Review" tag
    "RetrieveCalculationController.retrieveCalculation" -> Seq(
      Outcome(200, None, 88),
      Outcome(404, Some("MATCHING_RESOURCE_NOT_FOUND"), 12)
    ),
    "TriggerBsasController.triggerBsas" -> Seq(
      Outcome(200, None, 80),
      Outcome(400, Some("RULE_TAX_YEAR_NOT_SUPPORTED"), 12),
      Outcome(500, Some("INTERNAL_SERVER_ERROR"), 8)
    )
  )

  private val errorMessages: Map[String, String] = Map(
    "MATCHING_RESOURCE_NOT_FOUND"    -> "Matching resource not found",
    "CLIENT_OR_AGENT_NOT_AUTHORISED" -> "The client and/or agent is not authorised",
    "RULE_TAX_YEAR_NOT_SUPPORTED"    -> "The tax year specified does not lie within the supported range",
    "FORMAT_NINO"                    -> "The provided NINO is invalid",
    "INTERNAL_SERVER_ERROR"          -> "An internal server error occurred"
  )
}
