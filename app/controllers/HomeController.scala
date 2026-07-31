package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.LandingPage

@Singleton
class HomeController @Inject() (
  mcc: MessagesControllerComponents,
  landingPage: LandingPage
) extends FrontendController(mcc) {

  def onPageLoad(): Action[AnyContent] = Action { implicit request =>
    Ok(landingPage())
  }
}
