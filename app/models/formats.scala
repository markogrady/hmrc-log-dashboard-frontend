package models

import play.api.libs.json._

/** Play JSON formats for the domain enums, stored as their case names. */
object EnumFormats {

  private def enumFormat[E](parse: String => E): Format[E] = Format(
    Reads {
      case JsString(value) =>
        try JsSuccess(parse(value))
        catch { case _: IllegalArgumentException => JsError(s"Unknown enum value: $value") }
      case other => JsError(s"Expected string, got $other")
    },
    Writes(e => JsString(e.toString))
  )

  implicit val logSeverityFormat: Format[LogSeverity]   = enumFormat(LogSeverity.valueOf)
  implicit val logEventKindFormat: Format[LogEventKind] = enumFormat(LogEventKind.valueOf)
  implicit val taxRegimeFormat: Format[TaxRegime]       = enumFormat(TaxRegime.valueOf)
}
