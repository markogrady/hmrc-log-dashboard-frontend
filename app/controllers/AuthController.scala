package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import config.AppConfig
import controllers.actions.SessionKeys
import models.forms.LoginForm
import views.html.LoginPage

@Singleton
class AuthController @Inject() (
  mcc: MessagesControllerComponents,
  appConfig: AppConfig,
  loginPage: LoginPage
) extends FrontendController(mcc) {

  def showLogin(continue: Option[String]): Action[AnyContent] = Action { implicit request =>
    Ok(loginPage(LoginForm.form, safeContinue(continue)))
  }

  def submitLogin(continue: Option[String]): Action[AnyContent] = Action { implicit request =>
    LoginForm.form
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(loginPage(formWithErrors, safeContinue(continue))),
        data =>
          if (data.username.trim.equalsIgnoreCase(appConfig.loginUsername) && data.password == appConfig.loginPassword)
            Redirect(safeContinue(continue).getOrElse(routes.DashboardController.onPageLoad(None, None, None).url))
              .withSession(SessionKeys.userId -> appConfig.loginUsername)
          else {
            val withError = LoginForm.form.fill(data.copy(password = "")).withError("username", "login.error.invalid")
            Unauthorized(loginPage(withError, safeContinue(continue)))
          }
      )
  }

  def signOut(): Action[AnyContent] = Action {
    Redirect(routes.HomeController.onPageLoad()).withNewSession
  }

  // Only ever redirect within this service: absolute or scheme-relative URLs are dropped.
  private def safeContinue(continue: Option[String]): Option[String] =
    continue.filter(c => c.startsWith("/") && !c.startsWith("//"))
}
