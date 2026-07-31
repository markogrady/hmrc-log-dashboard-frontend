package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import play.api.Logging

import config.AppConfig

/**
 * Eager singleton: on startup, seeds the sample dataset when the database is empty
 * (config key seed-on-startup). Preserves the original app's "first run just works" behaviour.
 */
@Singleton
class StartupSeeder @Inject() (seeder: SeederService, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends Logging {

  if (appConfig.seedOnStartup) {
    seeder.seed(force = false).failed.foreach { e =>
      logger.error("Startup seeding failed", e)
    }
  } else {
    logger.info("Startup seeding disabled (seed-on-startup = false)")
  }
}
