package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import controllers.actions.IdentifierAction
import services.FraudPreventionService
import views.html.{FraudPreventionPage, PageNotFound}

/** GET /dashboard/:appId/fraud-prevention-headers — header diversity checks over all of an application's logs. */
@Singleton
class FraudPreventionController @Inject() (
  mcc: MessagesControllerComponents,
  identify: IdentifierAction,
  fraudPreventionService: FraudPreventionService,
  fraudPreventionPage: FraudPreventionPage,
  pageNotFound: PageNotFound
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport {

  def onPageLoad(appId: UUID): Action[AnyContent] =
    identify.async { implicit request =>
      fraudPreventionService.report(appId).map {
        case None         => NotFound(pageNotFound())
        case Some(report) => Ok(fraudPreventionPage(report))
      }
    }
}
