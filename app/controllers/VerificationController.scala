package controllers

import javax.inject._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import play.api.mvc._
import services.EmailService

import scala.collection.immutable.Iterable

/**
 * This controller demonstrates how to use dependency injection to
 * bind a component into a controller class. The class creates an
 * `Action` that shows an incrementing count to users. The [[EmailService]]
 * object is injected by the Guice dependency injection system.
 */
@Singleton
class VerificationController @Inject()(system: ActorSystem, emailService: EmailService) extends Controller {
  import EmailService._

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val materializer = ActorMaterializer()(system)

  /**
   * Create an action that responds with the [[EmailService]]'s current
   * count. The result is plain text. This `Action` is mapped to
   * `GET /count` requests by an entry in the `routes` config file.
   */
  def verifyByEmail = Action.async(parse.json) { request =>
    val verifySource = Source[EmailVerification](Iterable(request.body.as[EmailVerification]))
    val response = verifySource.map(verify => emailService.sendVerifyEmail(verify)).via(serializeToJsonResponse).runWith(Sink.head)
    response.map(result =>
//      Logger.info("result: " + result._2.utf8String)
//      Result(
//        ResponseHeader(result._1),
//        HttpEntity.Strict(result._2, None)
//      )
//    }
      if (result._1 == 200) {
        Ok(result._2)
      } else {
        BadRequest(result._2)
      }
    )
  }
}
