package handlers

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.MessagesApi
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler
import views.html.{ErrorTemplate, PageNotFound}

@Singleton
class ErrorHandler @Inject() (
  val messagesApi: MessagesApi,
  errorTemplate: ErrorTemplate,
  pageNotFound: PageNotFound
)(implicit protected val ec: ExecutionContext)
    extends FrontendErrorHandler {

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit
    request: RequestHeader
  ): Future[Html] =
    Future.successful(errorTemplate(pageTitle, heading, message))

  override def notFoundTemplate(implicit request: RequestHeader): Future[Html] =
    Future.successful(pageNotFound())
}
