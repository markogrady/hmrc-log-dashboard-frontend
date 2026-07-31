package models

/** Severity of a log line. Order matters: comparisons use ordinal (Debug < Info < Warn < Error). */
enum LogSeverity {
  case Debug, Info, Warn, Error
}

object LogSeverity {
  given Ordering[LogSeverity] = Ordering.by(_.ordinal)
}

/** What a log line represents within a request's lifecycle. */
enum LogEventKind {
  case Other, Start, Success, Failure
}

/** Tax regime an endpoint belongs to. */
enum TaxRegime {
  case Unknown, Itsa, Vat
}

/** Health tag for an endpoint or API area. Order matters: Correct < Review < Fix, so `max` picks the worst. */
enum HealthTag {
  case Correct, Review, Fix
}

object HealthTag {
  given Ordering[HealthTag] = Ordering.by(_.ordinal)
}
