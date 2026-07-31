package repositories

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.mongodb.scala.model.{Filters, ReplaceOptions, Sorts}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import models.HmrcApplication

@Singleton
class ApplicationRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[HmrcApplication](
      collectionName = "applications",
      mongoComponent = mongoComponent,
      domainFormat = HmrcApplication.mongoFormat,
      indexes = Seq.empty
    ) {

  // Applications are seeded/auto-registered and re-registering must be idempotent.
  override lazy val requiresTtlIndex: Boolean = false

  def findAll(): Future[Seq[HmrcApplication]] =
    collection.find().sort(Sorts.ascending("name")).toFuture()

  def findById(id: UUID): Future[Option[HmrcApplication]] =
    collection.find(Filters.equal("_id", id.toString)).headOption()

  def upsert(application: HmrcApplication): Future[Unit] =
    collection
      .replaceOne(Filters.equal("_id", application.id.toString), application, ReplaceOptions().upsert(true))
      .toFuture()
      .map(_ => ())

  def deleteAll(): Future[Unit] =
    collection.deleteMany(Filters.empty()).toFuture().map(_ => ())
}
