package services

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import java.util.regex.{Matcher, Pattern}
import javax.inject.Singleton

import models._

/**
 * Parses log lines in the hmrc/vat-api logback pattern:
 * `%date{ISO8601} level=[%level] logger=[%logger] thread=[%thread] rid=[..] appId=[..] clientId=[..] message=[..]`.
 * The appId/clientId MDC fields are optional so plain vat-api lines still parse. Five further optional
 * MDC fields may follow clientId, in this order, carrying the request's fraud prevention headers:
 * `govClientPublicIP=[..] govVendorPublicIP=[..] govClientDeviceID=[..] govClientLocalIPs=[..] govVendorLicenseIDs=[..]`.
 * Never throws: unparseable lines come back with parsed = false and the raw line preserved.
 */
@Singleton
class LogLineParser {

  import LogLineParser._

  def parse(rawLine: String): LogEntry = {
    val unparsed = LogEntry(
      rawLine = rawLine,
      message = rawLine,
      parsed = false,
      timestampUtc = Instant.now()
    )

    if (rawLine == null || rawLine.trim.isEmpty) unparsed
    else {
      val outer = OuterLine.matcher(rawLine.trim)
      if (!outer.matches()) unparsed
      else
        parseTimestamp(outer.group("ts")) match {
          case None            => unparsed
          case Some(timestamp) =>
            val base = LogEntry(
              rawLine = rawLine,
              parsed = true,
              timestampUtc = timestamp,
              level = parseLevel(outer.group("level")),
              logger = noneIfBlank(outer.group("logger")),
              thread = noneIfBlank(outer.group("thread")),
              requestId = noneIfBlank(outer.group("rid")),
              clientId = noneIfBlank(outer.group("clientId")),
              applicationId = parseUuid(outer.group("appId")),
              govClientPublicIp = noneIfBlank(outer.group("clientPublicIp")),
              govVendorPublicIp = noneIfBlank(outer.group("vendorPublicIp")),
              govClientDeviceId = noneIfBlank(outer.group("clientDeviceId")),
              govClientLocalIps = noneIfBlank(outer.group("clientLocalIps")),
              govVendorLicenseIds = noneIfBlank(outer.group("vendorLicenseIds")),
              message = outer.group("msg")
            )
            parseMessage(base)
        }
    }
  }

  private def parseMessage(entry: LogEntry): LogEntry = {
    val message = entry.message

    val prefix                   = MessagePrefix.matcher(message)
    val (controller, endpoint)   =
      if (prefix.matches()) (Some(prefix.group("ctrl")), Some(prefix.group("ep"))) else (None, None)

    val correlationId = firstGroup(CorrelationId, message, "cid")

    val identifierMatcher       = IdentifierRegex.matcher(message)
    val (identifier, regime)    =
      if (identifierMatcher.find())
        (
          Some(identifierMatcher.group("idf")),
          if (identifierMatcher.group("kind").equalsIgnoreCase("VRN")) TaxRegime.Vat else TaxRegime.Itsa
        )
      else (None, entry.regime)

    val statusCode = firstGroup(StatusCode, message, "status").map(_.toInt)
    val errorCode  = firstGroup(ErrorCode, message, "code")

    val enriched = entry.copy(
      controller = controller,
      endpoint = endpoint,
      correlationId = correlationId,
      identifier = identifier,
      regime = regime,
      statusCode = statusCode,
      errorCode = errorCode
    )

    val classified = enriched.copy(kind = classifyKind(enriched))

    ItsaEndpointCatalog.tryMatch(classified.controller, classified.endpoint) match {
      case Some(definition) =>
        classified.copy(
          apiArea = Some(definition.apiArea),
          endpointKey = Some(definition.key),
          httpMethod = Some(definition.httpMethod),
          pathTemplate = Some(definition.pathTemplate),
          regime = definition.regime
        )
      case None if classified.controller.isDefined =>
        classified.copy(
          apiArea = Some("Unrecognised"),
          endpointKey = Some(s"${classified.controller.get}.${classified.endpoint.getOrElse("")}")
        )
      case None => classified
    }
  }

  private def classifyKind(entry: LogEntry): LogEventKind = {
    val message = entry.message.toLowerCase

    if (message.contains("successfully") || message.contains("success response received"))
      LogEventKind.Success
    else if (
      entry.statusCode.exists(_ >= 400) ||
      message.contains("error response received") ||
      (entry.level.ordinal >= LogSeverity.Warn.ordinal && entry.errorCode.isDefined)
    )
      LogEventKind.Failure
    else if (entry.controller.isDefined && entry.correlationId.isDefined)
      LogEventKind.Start
    else
      LogEventKind.Other
  }
}

object LogLineParser {

  private val OuterLine: Pattern = Pattern.compile(
    "^(?<ts>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s+" +
      "level=\\[(?<level>[A-Z]+)\\]\\s+" +
      "logger=\\[(?<logger>[^\\]]*)\\]\\s+" +
      "(?:thread=\\[(?<thread>[^\\]]*)\\]\\s+)?" +
      "(?:rid=\\[(?<rid>[^\\]]*)\\]\\s+)?" +
      "(?:user=\\[(?<user>[^\\]]*)\\]\\s+)?" +
      "(?:appId=\\[(?<appId>[^\\]]*)\\]\\s+)?" +
      "(?:clientId=\\[(?<clientId>[^\\]]*)\\]\\s+)?" +
      "(?:govClientPublicIP=\\[(?<clientPublicIp>[^\\]]*)\\]\\s+)?" +
      "(?:govVendorPublicIP=\\[(?<vendorPublicIp>[^\\]]*)\\]\\s+)?" +
      "(?:govClientDeviceID=\\[(?<clientDeviceId>[^\\]]*)\\]\\s+)?" +
      "(?:govClientLocalIPs=\\[(?<clientLocalIps>[^\\]]*)\\]\\s+)?" +
      "(?:govVendorLicenseIDs=\\[(?<vendorLicenseIds>[^\\]]*)\\]\\s+)?" +
      "message=\\[(?<msg>.*)\\]\\s*$"
  )

  private val MessagePrefix: Pattern =
    Pattern.compile("^\\[(?<ctrl>[A-Za-z0-9_]+)\\]\\[(?<ep>[A-Za-z0-9_]+)\\]\\s*(?<rest>.*)$", Pattern.DOTALL)

  private val CorrelationId: Pattern =
    Pattern.compile("correlationId\\s*:?\\s*(?<cid>[A-Za-z0-9-]+)", Pattern.CASE_INSENSITIVE)

  private val IdentifierRegex: Pattern =
    Pattern.compile("for\\s+(?<kind>NINO|VRN)\\s*:\\s*(?<idf>[A-Z0-9]+)", Pattern.CASE_INSENSITIVE)

  private val StatusCode: Pattern =
    Pattern.compile("status\\s*:?\\s*(?<status>\\d{3})", Pattern.CASE_INSENSITIVE)

  private val ErrorCode: Pattern =
    Pattern.compile("\"code\"\\s*:\\s*\"(?<code>[A-Z0-9_]+)\"")

  private val timestampFormats: List[DateTimeFormatter] = List(
    "yyyy-MM-dd HH:mm:ss,SSS",
    "yyyy-MM-dd HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss,SSS",
    "yyyy-MM-dd'T'HH:mm:ss.SSS"
  ).map(DateTimeFormatter.ofPattern)

  private def parseTimestamp(value: String): Option[Instant] =
    timestampFormats.view.flatMap { format =>
      try Some(LocalDateTime.parse(value, format).toInstant(ZoneOffset.UTC))
      catch { case _: DateTimeParseException => None }
    }.headOption

  private def parseLevel(level: String): LogSeverity = level.toUpperCase match {
    case "WARN" | "WARNING" => LogSeverity.Warn
    case "ERROR" | "FATAL"  => LogSeverity.Error
    case "DEBUG" | "TRACE"  => LogSeverity.Debug
    case _                  => LogSeverity.Info
  }

  private def parseUuid(value: String): Option[UUID] =
    Option(value).filter(_.nonEmpty).flatMap { v =>
      try Some(UUID.fromString(v))
      catch { case _: IllegalArgumentException => None }
    }

  private def noneIfBlank(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def firstGroup(pattern: Pattern, input: String, group: String): Option[String] = {
    val m: Matcher = pattern.matcher(input)
    if (m.find()) Some(m.group(group)) else None
  }
}
