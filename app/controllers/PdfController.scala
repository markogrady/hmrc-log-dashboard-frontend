package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.HttpEntity
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, ResponseHeader, Result}
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import controllers.actions.IdentifierAction
import models.viewmodels.MonthOption
import services.{DashboardService, PdfReportService}

/** GET /dashboard/export/pdf?appId=...&month=yyyy-MM&client=... — the issues/warnings report. */
@Singleton
class PdfController @Inject() (
  mcc: MessagesControllerComponents,
  identify: IdentifierAction,
  dashboardService: DashboardService,
  pdfReportService: PdfReportService
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) {

  def exportPdf(appId: UUID, month: String, client: Option[String]): Action[AnyContent] = identify.async {
    MonthOption.parse(month) match {
      case None    => Future.successful(BadRequest("month must be yyyy-MM"))
      case Some(m) =>
        dashboardService.reportData(appId, client.filter(_.nonEmpty), m.year, m.month).map {
          case None         => NotFound("application not found")
          case Some(report) =>
            val bytes = pdfReportService.render(report)
            Result(
              header = ResponseHeader(
                OK,
                Map(
                  "Content-Disposition" -> s"attachment; filename=api-issues-report-$month-$appId.pdf"
                )
              ),
              body = HttpEntity.Strict(ByteString(bytes), Some("application/pdf"))
            )
        }
    }
  }
}
