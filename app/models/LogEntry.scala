package models

import java.time.Instant
import java.util.UUID

/** One ingested log line. Unparseable lines are kept with parsed = false and rawLine preserved. */
final case class LogEntry(
  rawLine: String,
  timestampUtc: Instant,
  level: LogSeverity = LogSeverity.Info,
  logger: Option[String] = None,
  thread: Option[String] = None,
  requestId: Option[String] = None,
  applicationId: Option[UUID] = None,
  clientId: Option[String] = None,
  correlationId: Option[String] = None,
  controller: Option[String] = None,
  endpoint: Option[String] = None,
  apiArea: Option[String] = None,
  endpointKey: Option[String] = None,
  httpMethod: Option[String] = None,
  pathTemplate: Option[String] = None,
  regime: TaxRegime = TaxRegime.Unknown,
  kind: LogEventKind = LogEventKind.Other,
  statusCode: Option[Int] = None,
  errorCode: Option[String] = None,
  identifier: Option[String] = None,
  message: String,
  parsed: Boolean
)
