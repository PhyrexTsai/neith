package EmailSpec

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.services.EmailService.{SendEmailAck, serializeToJsonResponse}
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.{JsBoolean, JsObject, JsString}

import scala.concurrent.Await

/**
  * Created by jameshsiao on 12/12/16.
  */
class EmailUnitTest extends PlaySpec with OneAppPerSuite {

  import scala.concurrent.duration._

  implicit lazy val materializer: Materializer = app.materializer

  "Serialize SendMailAck to Json" should {

    "return true if the data is valid" in {
      val sendMailAckFlow = Source.single(
          SendEmailAck( Some(true) )
      ).via(serializeToJsonResponse).runWith(Sink.head)

      val result = Await.result(sendMailAckFlow, 5.seconds)
      result._1 mustBe 200
      result._2 mustBe JsObject(
        List(("isSuccess", JsBoolean(true)))
      )
    }

    "return BAD Request and error message if failed" in {
      val sendMailAckFlow = Source.single(
        SendEmailAck( Some(false), Some("Send mail failed") )
      ).via(serializeToJsonResponse).runWith(Sink.head)

      val result = Await.result(sendMailAckFlow, 5.seconds)
      result._1 mustBe 400
      result._2 mustBe JsObject(
        List(
          ("isSuccess", JsBoolean(false)),
          ("error", JsString("Send mail failed"))
        )
      )
    }

  }

}
