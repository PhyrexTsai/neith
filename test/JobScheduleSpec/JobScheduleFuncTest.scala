package JobScheduleSpec

import akka.stream.Materializer
import me.mig.mars.models.NotificationType
import me.mig.mars.repositories.mysql.FusionDatabase
import me.mig.mars.services.JobScheduleService.{CreateJob, CreateJobAck}
import org.joda.time.DateTime
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.{Application, Logger}
import play.api.libs.json.Json
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._

import scala.collection.immutable.HashMap
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 12/14/16.
  */
class JobScheduleFuncTest extends PlaySpec with OneAppPerSuite {

  implicit lazy val materializer: Materializer = app.materializer
  implicit val CreateJobAckReads = Json.reads[CreateJobAck]
  implicit val CreateJobWrites = Json.writes[CreateJob]

  "RESTful POST /createJob" should {
    val fakeRequest = FakeRequest(POST, "/createJob")

    "Return success with valid request" in {
      val startTime = DateTime.now().plusMinutes(1)
      val interval = 2 minutes
      val result = route(app, fakeRequest
        .withJsonBody(
          Json.toJson(CreateJob("TestJob", List(1), List(3, 6), startTime.getMillis, None, interval.toMillis, NotificationType.PUSH.toString, "job1", HashMap("web" -> "yes")))
        )
      ).get

      status(result) mustBe OK
      contentAsJson(result).as[CreateJobAck].success mustBe true
    }

    "Got error with invalid json format" in {
      val result = route(app, fakeRequest.withJsonBody(Json.parse("""{"create": "job"}"""))).get

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include ("error.path.missing")
    }

    "Got error with invalid content" in {
      val startTime = DateTime.now().minusMinutes(1).getMillis
      val interval = 1 minutes
      val result = route(app, fakeRequest
        .withJsonBody(
          Json.toJson(CreateJob("TestInvalidJob", List(1), List(4, 8), startTime, None, interval.toMillis, "None", "job2", HashMap("hi" -> "five")))
        )
      ).get

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include ("Creating a scheduled job encounters error:")
    }

    "Search users with labels" in {
      val app = FakeApplication(additionalConfiguration = Map(
        "slick.dbs.default.driver" -> "slick.driver.H2Driver$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:play"
      ))

      val fusionDb = Application.instanceCache[FusionDatabase].apply(app)
      val results = Await.result(fusionDb.getUserTokensByLabelAndCountry(List(1), List(4, 8)), 15 seconds)
      Logger.info("results: " + results)
    }
  }

}
