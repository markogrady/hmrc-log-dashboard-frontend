package controllers

import java.time.{LocalDate, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import controllers.actions.IdentifierAction
import models.viewmodels.MonthOption
import services.DashboardService
import views.html.EndpointDetailPage

@Singleton
class EndpointDetailController @Inject() (
  mcc: MessagesControllerComponents,
  identify: IdentifierAction,
  dashboardService: DashboardService,
  detailPage: EndpointDetailPage
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport {

  def onPageLoad(appId: UUID, endpointKey: String, month: Option[String], client: Option[String]): Action[AnyContent] =
    identify.async { implicit request =>
      val now           = LocalDate.now(ZoneOffset.UTC)
      val selectedMonth = month.flatMap(MonthOption.parse).getOrElse(MonthOption(now.getYear, now.getMonthValue))
      val selectedClient = client.filter(_.nonEmpty)

      for {
        detail   <- dashboardService.endpointDetail(appId, endpointKey, selectedClient, selectedMonth.year, selectedMonth.month)
        rawLines <- dashboardService.rawLinesFor(detail.recentRequests.map(_.correlationId))
      } yield Ok(detailPage(appId, endpointKey, detail, selectedMonth, selectedClient, rawLines))
    }
}
