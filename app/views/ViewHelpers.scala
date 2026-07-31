package views

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.Locale

import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.tag.Tag

import models.HealthTag

object ViewHelpers {

  def healthTag(tag: HealthTag): Tag = tag match {
    case HealthTag.Fix     => Tag(content = Text("Fix"), classes = "govuk-tag--red")
    case HealthTag.Review  => Tag(content = Text("Review"), classes = "govuk-tag--yellow")
    case HealthTag.Correct => Tag(content = Text("Correct"), classes = "govuk-tag--green")
  }

  def statusTag(status: Int): Tag =
    if (status >= 500) Tag(content = Text(status.toString), classes = "govuk-tag--red")
    else if (status >= 400) Tag(content = Text(status.toString), classes = "govuk-tag--yellow")
    else Tag(content = Text(status.toString), classes = "govuk-tag--green")

  private def fmt(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.UK).withZone(ZoneOffset.UTC)

  private val dayMonthFormat      = fmt("d MMMM")
  private val lastCalledFormat    = fmt("d MMM HH:mm")
  private val timestampFormat     = fmt("yyyy-MM-dd HH:mm:ss")

  def formatDayMonth(instant: Instant): String   = dayMonthFormat.format(instant)
  def formatLastCalled(instant: Instant): String = lastCalledFormat.format(instant)
  def formatTimestamp(instant: Instant): String  = timestampFormat.format(instant)

  def formatPercent(value: Double): String =
    if (value > 0 && value < 1) "< 1%"
    else if (value == value.floor) s"${value.toInt}%"
    else s"$value%"
}
