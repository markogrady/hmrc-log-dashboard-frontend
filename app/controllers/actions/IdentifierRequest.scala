package controllers.actions

import play.api.mvc.{Request, WrappedRequest}

final case class IdentifierRequest[A](request: Request[A], userId: String) extends WrappedRequest[A](request)
