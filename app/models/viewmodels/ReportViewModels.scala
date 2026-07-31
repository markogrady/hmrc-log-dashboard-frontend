package models.viewmodels

import java.time.Instant

final case class ReportIssueRow(
  status: Int,
  errorCode: Option[String],
  count: Int,
  percentOfRequests: Double,
  sampleMessage: String
)

final case class ReportEndpointIssues(
  apiArea: String,
  displayName: String,
  httpMethod: String,
  pathTemplate: String,
  totalRequests: Int,
  failureCount: Int,
  rows: Seq[ReportIssueRow]
)

final case class ReportFailedRequestRow(
  timestampUtc: Instant,
  endpointDisplay: String,
  clientId: Option[String],
  correlationId: String,
  status: Int,
  errorCode: Option[String],
  message: String
)

final case class ReportWarningRow(
  timestampUtc: Instant,
  apiArea: Option[String],
  endpoint: Option[String],
  clientId: Option[String],
  message: String
)

final case class DashboardReportData(
  application: ApplicationOption,
  clientId: Option[String],
  year: Int,
  month: Int,
  summary: DashboardSummary,
  issues: Seq[ReportEndpointIssues],
  totalFailedRequestCount: Int,
  failedRequests: Seq[ReportFailedRequestRow],
  totalWarningCount: Int,
  warnings: Seq[ReportWarningRow]
)
