package models.viewmodels

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate}
import java.util.{Locale, UUID}

import models.{EndpointDefinition, HealthTag}

final case class ApplicationOption(id: UUID, name: String, environment: String, softwareVersion: String)

final case class MonthOption(year: Int, month: Int) {
  val value: String   = f"$year%04d-$month%02d"
  def display: String =
    LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK))
}

object MonthOption {
  /** Parses the yyyy-MM query-parameter form. */
  def parse(value: String): Option[MonthOption] =
    value.trim match {
      case s if s.matches("""\d{4}-\d{2}""") =>
        val year  = s.take(4).toInt
        val month = s.drop(5).toInt
        Option.when(month >= 1 && month <= 12)(MonthOption(year, month))
      case _ => None
    }
}

final case class EndpointSummaryRow(
  endpointKey: String,
  httpMethod: String,
  displayName: String,
  pathTemplate: String,
  requests: Int,
  successCount: Int,
  failureCount: Int,
  lastCalledUtc: Instant,
  tag: HealthTag
) {
  def successPercent: Double =
    if (requests == 0) 100 else BigDecimal(successCount * 100.0 / requests).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble
}

final case class ApiAreaSummary(apiArea: String, tag: HealthTag, totalRequests: Int, endpoints: Seq[EndpointSummaryRow])

final case class DashboardSummary(lastApiRequestUtc: Option[Instant], totalRequests: Int, areas: Seq[ApiAreaSummary])

final case class ErrorBreakdownRow(action: String, requests: Int, percent: Double, details: String)

final case class RequestRow(
  timestampUtc: Instant,
  clientId: Option[String],
  identifier: Option[String],
  status: Int,
  correlationId: String
)

final case class EndpointDetailResult(
  definition: Option[EndpointDefinition],
  totalRequests: Int,
  failureCount: Int,
  breakdown: Seq[ErrorBreakdownRow],
  recentRequests: Seq[RequestRow]
)
