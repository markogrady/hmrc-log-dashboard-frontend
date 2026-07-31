package services

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import models._
import models.viewmodels._
import repositories.{ApplicationRepository, LogEntryRepository}

/** Aggregates parsed log entries into the per-area / per-endpoint dashboard view. */
@Singleton
class DashboardService @Inject() (
  applicationRepository: ApplicationRepository,
  logEntryRepository: LogEntryRepository
)(implicit ec: ExecutionContext) {

  import DashboardService._

  def applications(): Future[Seq[ApplicationOption]] =
    applicationRepository
      .findAll()
      .map(_.map(a => ApplicationOption(a.id, a.name, a.environment, a.softwareVersion)))

  def clients(appId: UUID): Future[Seq[String]] =
    logEntryRepository.distinctClients(appId)

  def months(appId: UUID): Future[Seq[MonthOption]] =
    logEntryRepository
      .distinctMonths(appId)
      .map(_.map(MonthOption.apply.tupled).sortBy(m => (m.year, m.month)).reverse)

  def summary(appId: UUID, clientId: Option[String], year: Int, month: Int): Future[DashboardSummary] = {
    val (monthStart, monthEnd) = monthWindow(year, month)
    for {
      lines       <- logEntryRepository.findMonthSlice(appId, monthStart, monthEnd, clientId)
      lastRequest <- logEntryRepository.lastTimestamp(appId)
    } yield buildSummary(lines, lastRequest)
  }

  def endpointDetail(
    appId: UUID,
    endpointKey: String,
    clientId: Option[String],
    year: Int,
    month: Int
  ): Future[EndpointDetailResult] = {
    val (monthStart, monthEnd) = monthWindow(year, month)
    logEntryRepository
      .findEndpointMonthSlice(appId, endpointKey, monthStart, monthEnd, clientId)
      .map(lines => buildEndpointDetail(endpointKey, lines))
  }

  /**
   * Everything the PDF issues/warnings report needs for one app + month (+ optional client filter).
   * None when the application does not exist. Warnings are matched on clientId strictly, so warning
   * lines without a clientId are excluded when a client filter is applied.
   */
  def reportData(appId: UUID, clientId: Option[String], year: Int, month: Int): Future[Option[DashboardReportData]] =
    applicationRepository.findById(appId).flatMap {
      case None      => Future.successful(None)
      case Some(app) =>
        val (monthStart, monthEnd) = monthWindow(year, month)
        for {
          summaryData <- summary(appId, clientId, year, month)
          lines       <- logEntryRepository.findMonthSlice(appId, monthStart, monthEnd, clientId)
          warnings    <- logEntryRepository.findWarnings(appId, monthStart, monthEnd, clientId)
        } yield Some(
          buildReport(
            ApplicationOption(app.id, app.name, app.environment, app.softwareVersion),
            clientId,
            year,
            month,
            summaryData,
            lines,
            warnings
          )
        )
    }

  def rawLines(correlationId: String): Future[Seq[String]] =
    logEntryRepository.findByCorrelationId(correlationId).map(_.map(_.rawLine))

  /** Raw lines for many requests at once (endpoint-detail expanders), keyed by correlationId. */
  def rawLinesFor(correlationIds: Seq[String]): Future[Map[String, Seq[String]]] =
    logEntryRepository
      .findByCorrelationIds(correlationIds)
      .map(_.groupBy(_.correlationId.getOrElse("")).view.mapValues(_.map(_.rawLine)).toMap)
}

object DashboardService {

  def monthWindow(year: Int, month: Int): (Instant, Instant) = {
    val start = LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC).toInstant
    val end   = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant
    (start, end)
  }

  /** One request = one correlationId; its outcome is the worst line observed. */
  private final case class RequestOutcome(
    correlationId: String,
    endpointKey: String,
    apiArea: String,
    firstSeen: Instant,
    lastSeen: Instant,
    worstStatus: Int,
    clientId: Option[String],
    identifier: Option[String],
    errorCode: Option[String]
  )

  private def toRequests(lines: Seq[LogEntry]): Seq[RequestOutcome] =
    lines
      .filter(l => l.correlationId.isDefined)
      .groupBy(_.correlationId.get)
      .map { case (correlationId, group) =>
        RequestOutcome(
          correlationId = correlationId,
          endpointKey = group.head.endpointKey.getOrElse(""),
          apiArea = group.head.apiArea.getOrElse("Unrecognised"),
          firstSeen = group.map(_.timestampUtc).min,
          lastSeen = group.map(_.timestampUtc).max,
          worstStatus = worstStatus(group),
          clientId = group.flatMap(_.clientId).headOption,
          identifier = group.flatMap(_.identifier).headOption,
          errorCode = group.flatMap(_.errorCode).headOption
        )
      }
      .toSeq

  /** Worst (max) status across a request's lines; default 500 if any Failure-kind line, else 200. */
  def worstStatus(group: Seq[LogEntry]): Int =
    group.flatMap(_.statusCode).maxOption.getOrElse(
      if (group.exists(_.kind == LogEventKind.Failure)) 500 else 200
    )

  def endpointTag(total: Int, failures: Int, any5xx: Boolean): HealthTag =
    if (any5xx || (total > 0 && failures * 100.0 / total > 25)) HealthTag.Fix
    else if (failures > 0) HealthTag.Review
    else HealthTag.Correct

  def buildSummary(lines: Seq[LogEntry], lastRequest: Option[Instant]): DashboardSummary = {
    val requests = toRequests(lines.filter(_.endpointKey.isDefined))

    val areas = requests
      .groupBy(_.apiArea)
      .map { case (apiArea, areaRequests) =>
        val rows = areaRequests
          .groupBy(_.endpointKey)
          .map { case (endpointKey, endpointRequests) =>
            val definition = ItsaEndpointCatalog.findByKey(endpointKey)
            val total      = endpointRequests.size
            val failures   = endpointRequests.count(_.worstStatus >= 400)
            val any5xx     = endpointRequests.exists(_.worstStatus >= 500)

            EndpointSummaryRow(
              endpointKey = endpointKey,
              httpMethod = definition.map(_.httpMethod).getOrElse("—"),
              displayName = definition.map(_.displayName).getOrElse(endpointKey),
              pathTemplate = definition.map(_.pathTemplate).getOrElse(""),
              requests = total,
              successCount = total - failures,
              failureCount = failures,
              lastCalledUtc = endpointRequests.map(_.lastSeen).max,
              tag = endpointTag(total, failures, any5xx)
            )
          }
          .toSeq
          .sortBy(-_.requests)

        ApiAreaSummary(
          apiArea = apiArea,
          tag = rows.map(_.tag).max,
          totalRequests = rows.map(_.requests).sum,
          endpoints = rows
        )
      }
      .toSeq
      .sortBy(a => (-a.tag.ordinal, -a.totalRequests))

    DashboardSummary(lastRequest, requests.size, areas)
  }

  private val MaxReportFailedRequestRows = 1000
  private val MaxReportWarningRows       = 500

  private final case class ReportRequest(
    correlationId: String,
    endpointKey: String,
    apiArea: String,
    timestamp: Instant,
    clientId: Option[String],
    status: Int,
    errorCode: Option[String],
    message: String
  )

  def buildReport(
    application: ApplicationOption,
    clientId: Option[String],
    year: Int,
    month: Int,
    summary: DashboardSummary,
    lines: Seq[LogEntry],
    warningLines: Seq[LogEntry]
  ): DashboardReportData = {
    // Failed-request rows carry the worst line's message; recompute per-correlation with messages.
    val requests = lines
      .filter(l => l.correlationId.isDefined && l.endpointKey.isDefined)
      .groupBy(_.correlationId.get)
      .map { case (correlationId, group) =>
        ReportRequest(
          correlationId = correlationId,
          endpointKey = group.head.endpointKey.get,
          apiArea = group.head.apiArea.getOrElse("Unrecognised"),
          timestamp = group.map(_.timestampUtc).min,
          clientId = group.flatMap(_.clientId).headOption,
          status = worstStatus(group),
          errorCode = group.flatMap(_.errorCode).headOption,
          message = group.maxBy(l => (l.statusCode.getOrElse(-1), l.timestampUtc)).message
        )
      }
      .toSeq

    val failed           = requests.filter(_.status >= 400).sortBy(_.timestamp)
    val totalsByEndpoint = requests.groupBy(_.endpointKey).view.mapValues(_.size).toMap

    val issues = failed
      .groupBy(_.endpointKey)
      .map { case (endpointKey, group) =>
        val definition    = ItsaEndpointCatalog.findByKey(endpointKey)
        val endpointTotal = totalsByEndpoint(endpointKey)
        val rows = group
          .groupBy(r => (r.status, r.errorCode))
          .map { case ((status, errorCode), rg) =>
            ReportIssueRow(
              status = status,
              errorCode = errorCode,
              count = rg.size,
              percentOfRequests =
                if (endpointTotal == 0) 0
                else BigDecimal(rg.size * 100.0 / endpointTotal).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble,
              sampleMessage = rg.head.message
            )
          }
          .toSeq
          .sortBy(r => (-r.count, -r.status))

        ReportEndpointIssues(
          apiArea = group.head.apiArea,
          displayName = definition.map(_.displayName).getOrElse(endpointKey),
          httpMethod = definition.map(_.httpMethod).getOrElse("—"),
          pathTemplate = definition.map(_.pathTemplate).getOrElse(""),
          totalRequests = endpointTotal,
          failureCount = group.size,
          rows = rows
        )
      }
      .toSeq
      .sortBy(-_.failureCount)

    val failedRows = failed
      .take(MaxReportFailedRequestRows)
      .map { r =>
        ReportFailedRequestRow(
          timestampUtc = r.timestamp,
          endpointDisplay = ItsaEndpointCatalog.findByKey(r.endpointKey).map(_.displayName).getOrElse(r.endpointKey),
          clientId = r.clientId,
          correlationId = r.correlationId,
          status = r.status,
          errorCode = r.errorCode,
          message = r.message
        )
      }

    val warnings = warningLines
      .take(MaxReportWarningRows)
      .map(w => ReportWarningRow(w.timestampUtc, w.apiArea, w.endpoint, w.clientId, w.message))

    DashboardReportData(
      application = application,
      clientId = clientId.filter(_.nonEmpty),
      year = year,
      month = month,
      summary = summary,
      issues = issues,
      totalFailedRequestCount = failed.size,
      failedRequests = failedRows,
      totalWarningCount = warningLines.size,
      warnings = warnings
    )
  }

  def buildEndpointDetail(endpointKey: String, lines: Seq[LogEntry]): EndpointDetailResult = {
    val requests = toRequests(lines)
    val total    = requests.size

    val breakdown = requests
      .filter(_.worstStatus >= 400)
      .groupBy(r => (r.worstStatus, r.errorCode))
      .map { case ((status, errorCode), group) =>
        ErrorBreakdownRow(
          action = if (status >= 500) "Fix" else "Review",
          requests = group.size,
          percent =
            if (total == 0) 0
            else BigDecimal(group.size * 100.0 / total).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble,
          details = s"HTTP $status" + errorCode.fold("")(c => s" — $c")
        )
      }
      .toSeq
      .sortBy(-_.requests)

    val recent = requests
      .sortBy(_.firstSeen)
      .reverse
      .take(100)
      .map(r => RequestRow(r.firstSeen, r.clientId, r.identifier, r.worstStatus, r.correlationId))

    EndpointDetailResult(
      definition = ItsaEndpointCatalog.findByKey(endpointKey),
      totalRequests = total,
      failureCount = requests.count(_.worstStatus >= 400),
      breakdown = breakdown,
      recentRequests = recent
    )
  }
}
