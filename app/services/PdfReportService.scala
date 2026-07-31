package services

import java.io.ByteArrayOutputStream
import java.time.Instant
import javax.inject.{Inject, Singleton}

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder

import models.viewmodels.DashboardReportData

/** Renders the issues/warnings report as a PDF from the strict-XHTML print Twirl view. */
@Singleton
class PdfReportService @Inject() () {

  def render(report: DashboardReportData, generatedAtUtc: Instant = Instant.now()): Array[Byte] = {
    val html = views.html.printable.ReportView(report, generatedAtUtc).body
    val out  = new ByteArrayOutputStream()
    new PdfRendererBuilder()
      .useFastMode()
      .withHtmlContent(html, null)
      .toStream(out)
      .run()
    out.toByteArray
  }
}
