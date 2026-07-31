package controllers.testonly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import services.SeederService

/** Only reachable when running with -Dapplication.router=testOnlyDoNotUseInAppConf.Routes (MDTP test-only idiom). */
@Singleton
class SeedController @Inject() (
  mcc: MessagesControllerComponents,
  seeder: SeederService
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) {

  def seed(force: Boolean): Action[AnyContent] = Action.async {
    seeder.seed(force).map {
      case Some(result) =>
        Ok(
          Json.obj(
            "seeded"        -> true,
            "linesRead"     -> result.linesRead,
            "parsedCount"   -> result.parsedCount,
            "failedCount"   -> result.failedCount
          )
        )
      case None =>
        Ok(Json.obj("seeded" -> false, "reason" -> "log entries already present; use ?force=true to reseed"))
    }
  }
}
