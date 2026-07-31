package models.forms

import play.api.data.Form
import play.api.data.Forms._

final case class LoginData(username: String, password: String)

object LoginForm {

  val form: Form[LoginData] = Form(
    mapping(
      "username" -> text.verifying("login.error.username.required", _.trim.nonEmpty),
      "password" -> text.verifying("login.error.password.required", _.nonEmpty)
    )(LoginData.apply)(d => Some((d.username, d.password)))
  )
}
