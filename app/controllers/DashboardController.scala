package controllers

import java.time.{LocalDate, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import controllers.actions.IdentifierAction
import models.viewmodels.{DashboardPageViewModel, MonthOption}
import services.DashboardService
import views.html.{DashboardEmptyPage, DashboardPage}

@Singleton
class DashboardController @Inject() (
  mcc: MessagesControllerComponents,
  identify: IdentifierAction,
  dashboardService: DashboardService,
  dashboardPage: DashboardPage,
  emptyPage: DashboardEmptyPage
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport {

  def onPageLoad(appId: Option[UUID], month: Option[String], client: Option[String]): Action[AnyContent] =
    identify.async { implicit request =>
      dashboardService.applications().flatMap { apps =>
        apps match {
          case Seq() => Future.successful(Ok(emptyPage()))
          case _     =>
            val selectedApp = appId.flatMap(id => apps.find(_.id == id)).getOrElse(apps.head)
            for {
              months  <- dashboardService.months(selectedApp.id)
              clients <- dashboardService.clients(selectedApp.id)
              now            = LocalDate.now(ZoneOffset.UTC)
              currentMonth   = MonthOption(now.getYear, now.getMonthValue)
              selectedMonth  = month.flatMap(MonthOption.parse).orElse(months.headOption).getOrElse(currentMonth)
              selectedClient = client.filter(_.nonEmpty).filter(clients.contains)
              summary <- dashboardService.summary(selectedApp.id, selectedClient, selectedMonth.year, selectedMonth.month)
            } yield Ok(
              dashboardPage(
                DashboardPageViewModel(
                  applications = apps,
                  selectedApp = selectedApp,
                  months = months,
                  selectedMonth = selectedMonth,
                  isCurrentMonth = selectedMonth == currentMonth,
                  clients = clients,
                  selectedClient = selectedClient,
                  summary = summary
                )
              )
            )
        }
      }
    }
}
