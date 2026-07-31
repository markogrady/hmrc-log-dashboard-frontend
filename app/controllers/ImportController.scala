package controllers

import java.nio.file.Files
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import play.api.data.Form
import play.api.data.Forms._
import org.apache.pekko.stream.Materializer
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import config.AppConfig
import controllers.actions.IdentifierAction
import services.LogIngestionService
import views.html.ImportPage

@Singleton
class ImportController @Inject() (
  mcc: MessagesControllerComponents,
  identify: IdentifierAction,
  ingestionService: LogIngestionService,
  appConfig: AppConfig,
  importPage: ImportPage
)(implicit ec: ExecutionContext, mat: Materializer)
    extends FrontendController(mcc)
    with I18nSupport {

  private val pasteForm: Form[String] = Form(single("log-paste" -> text))

  private val allowedExtensions = Set("log", "txt")

  def onPageLoad(): Action[AnyContent] = identify { implicit request =>
    Ok(importPage(errors = Nil, result = None))
  }

  def submitPaste(): Action[AnyContent] = identify.async { implicit request =>
    val pasted = pasteForm.bindFromRequest().value.getOrElse("")
    if (pasted.trim.isEmpty)
      Future.successful(BadRequest(importPage(errors = Seq("import.error.emptyPaste"), result = None)))
    else
      ingestionService
        .ingest(pasted.linesIterator)
        .map(result => Ok(importPage(errors = Nil, result = Some(result))))
  }

  def submitUpload(): Action[Either[MaxSizeExceeded, MultipartFormData[TemporaryFile]]] =
    identify.async(parse.maxLength(appConfig.uploadMaxSizeBytes, parse.multipartFormData)) { implicit request =>
      request.body match {
        case Left(_) =>
          Future.successful(BadRequest(importPage(errors = Seq("import.error.tooLarge"), result = None)))
        case Right(data) =>
          data.file("log-file").filter(_.filename.nonEmpty) match {
            case None =>
              Future.successful(BadRequest(importPage(errors = Seq("import.error.noFile"), result = None)))
            case Some(file) =>
              val extension = file.filename.split('.').lastOption.map(_.toLowerCase).getOrElse("")
              if (!allowedExtensions.contains(extension))
                Future.successful(BadRequest(importPage(errors = Seq("import.error.fileType"), result = None)))
              else {
                val lines = Files.readAllLines(file.ref.path).asScala
                ingestionService
                  .ingest(lines)
                  .map(result => Ok(importPage(errors = Nil, result = Some(result))))
              }
          }
      }
    }
}
