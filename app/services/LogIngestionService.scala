package services

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import models.{HmrcApplication, LogEntry}
import repositories.{ApplicationRepository, LogEntryRepository}

final case class IngestResult(linesRead: Int, parsedCount: Int, failedCount: Int, failedSamples: Seq[String])

/** Parses raw log lines and persists them, auto-registering unknown application ids. */
@Singleton
class LogIngestionService @Inject() (
  applicationRepository: ApplicationRepository,
  logEntryRepository: LogEntryRepository,
  parser: LogLineParser
)(implicit ec: ExecutionContext) {

  private val BatchSize        = 500
  private val MaxFailedSamples = 5

  def ingest(lines: IterableOnce[String]): Future[IngestResult] =
    applicationRepository.findAll().flatMap { existing =>
      val knownAppIds = scala.collection.mutable.Set.from(existing.map(_.id))

      var linesRead     = 0
      var parsedCount   = 0
      val failedSamples = Vector.newBuilder[String]
      var failedKept    = 0
      val entries       = Vector.newBuilder[LogEntry]
      val newApps       = Vector.newBuilder[HmrcApplication]

      lines.iterator.filterNot(l => l == null || l.trim.isEmpty).foreach { line =>
        linesRead += 1
        val entry = parser.parse(line)

        if (entry.parsed) parsedCount += 1
        else if (failedKept < MaxFailedSamples) {
          failedSamples += line
          failedKept += 1
        }

        entry.applicationId.foreach { appId =>
          if (knownAppIds.add(appId)) {
            newApps += HmrcApplication(
              id = appId,
              name = s"Application ${appId.toString.take(8)}",
              environment = "Unknown"
            )
          }
        }

        entries += entry
      }

      val result = IngestResult(linesRead, parsedCount, linesRead - parsedCount, failedSamples.result())

      for {
        _ <- registerAll(newApps.result())
        _ <- insertBatches(entries.result())
      } yield result
    }

  private def registerAll(apps: Seq[HmrcApplication]): Future[Unit] =
    apps.foldLeft(Future.unit) { (acc, app) =>
      acc.flatMap(_ => applicationRepository.upsert(app))
    }

  private def insertBatches(entries: Seq[LogEntry]): Future[Unit] =
    entries.grouped(BatchSize).foldLeft(Future.unit) { (acc, batch) =>
      acc.flatMap(_ => logEntryRepository.insertMany(batch))
    }
}
