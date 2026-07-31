package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging

import models.HmrcApplication
import repositories.{ApplicationRepository, LogEntryRepository}

/**
 * Seeds the database with the deterministic sample dataset (~10k lines across 3 applications).
 * force = true wipes and regenerates; otherwise seeding is skipped when log entries already exist.
 */
@Singleton
class SeederService @Inject() (
  applicationRepository: ApplicationRepository,
  logEntryRepository: LogEntryRepository,
  ingestionService: LogIngestionService,
  generator: SampleLogGenerator
)(implicit ec: ExecutionContext)
    extends Logging {

  def seed(force: Boolean): Future[Option[IngestResult]] =
    for {
      existing <- logEntryRepository.count()
      result   <-
        if (existing > 0 && !force) {
          logger.info(s"Seeding skipped: $existing log entries already present")
          Future.successful(None)
        } else {
          for {
            _ <- if (force) wipe() else Future.unit
            _ <- registerSampleApplications()
            lines = generator.generate()
            result <- ingestionService.ingest(lines)
          } yield {
            logger.info(
              s"Seeded sample data: ${result.linesRead} lines read, ${result.parsedCount} parsed, " +
                s"${result.failedCount} failed, ${SampleLogGenerator.applications.size} applications"
            )
            Some(result)
          }
        }
    } yield result

  private def wipe(): Future[Unit] =
    for {
      _ <- logEntryRepository.deleteAll()
      _ <- applicationRepository.deleteAll()
    } yield logger.info("Wiped log entries and applications for reseed")

  private def registerSampleApplications(): Future[Unit] =
    SampleLogGenerator.applications.foldLeft(Future.unit) { (acc, app) =>
      acc.flatMap(_ =>
        applicationRepository.upsert(
          HmrcApplication(app.id, app.name, app.environment, app.softwareVersion)
        )
      )
    }
}
