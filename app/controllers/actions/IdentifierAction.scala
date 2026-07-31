package controllers.actions

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.Results.Redirect
import play.api.mvc._

/**
 * Auth seam (hmrc-frontend-scaffold.g8 shape). The production implementation on MDTP is backed by
 * auth-client; off-platform we bind SessionIdentifierAction (config-held credentials + session cookie).
 * Swapping in real auth is one Guice binding in config.Module.
 */
trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent] with ActionFunction[Request, IdentifierRequest]

object SessionKeys {
  val userId = "userId"
}

@Singleton
class SessionIdentifierAction @Inject() (
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends IdentifierAction {

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] =
    request.session.get(SessionKeys.userId) match {
      case Some(userId) => block(IdentifierRequest(request, userId))
      case None         =>
        Future.successful(Redirect(controllers.routes.AuthController.showLogin(Some(request.uri))))
    }
}
