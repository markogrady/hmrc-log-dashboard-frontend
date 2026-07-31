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
          if (validCredentials(data.username, data.password))
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

  // Constant-time comparison so response timing doesn't leak how much of a guess matched.
  private def validCredentials(username: String, password: String): Boolean = {
    def eq(a: String, b: String) =
      java.security.MessageDigest.isEqual(a.getBytes("UTF-8"), b.getBytes("UTF-8"))
    eq(username.trim.toLowerCase, appConfig.loginUsername.toLowerCase) & eq(password, appConfig.loginPassword)
  }

  // Only ever redirect within this service. Rejects scheme-relative (//host) and
  // backslash variants (/\host — browsers normalise \ to / in Location headers).
  private def safeContinue(continue: Option[String]): Option[String] =
    continue.filter(c => c.startsWith("/") && !c.startsWith("//") && !c.contains('\\'))
}
