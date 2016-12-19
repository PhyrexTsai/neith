package JobScheduleSpec

import akka.stream.Materializer
import me.mig.mars.models.NotificationType
import me.mig.mars.services.JobScheduleService.{CreateJob, CreateJobAck}
import org.joda.time.DateTime
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.duration._

/**
  * Created by jameshsiao on 12/14/16.
  */
class JobScheduleFuncTest extends PlaySpec with OneAppPerSuite {

  implicit lazy val materializer: Materializer = app.materializer

  "RESTful POST /createJob" should {
    val fakeRequest = FakeRequest(POST, "/createJob")

    "Return success with valid request" in {
      val startTime = DateTime.now().plusMinutes(1)
      val interval = 2 minutes
      val result = route(app, fakeRequest
        .withJsonBody(
          Json.toJson(CreateJob(startTime.getMillis, interval.toMillis, NotificationType.PUSH.id))
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
          Json.toJson(CreateJob(startTime, interval.toMillis, 99))
        )
      ).get

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include ("Creating a scheduled job encounters error:")
    }
  }

}
