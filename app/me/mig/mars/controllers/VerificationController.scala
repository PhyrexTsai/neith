package me.mig.mars.controllers

import javax.inject._

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.services.EmailService
import play.api.mvc._

import scala.collection.immutable.Iterable

/**
 * This controller demonstrates how to use dependency injection to
 * bind a component into a controller class. The class creates an
 * `Action` that shows an incrementing count to users. The [[EmailService]]
 * object is injected by the Guice dependency injection system.
 */
@Singleton
class VerificationController @Inject()(emailService: EmailService)(implicit system: ActorSystem, materializer: Materializer) extends Controller {
  import EmailService._

  import scala.concurrent.ExecutionContext.Implicits.global

  /**
   * Create an action that responds with the [[EmailService]]'s current
   * count. The result is plain text. This `Action` is mapped to
   * `GET /count` requests by an entry in the `routes` config file.
   */
  def verifyByEmail = Action.async(parse.json) { request =>
    val verifySource = Source[EmailVerification](Iterable(request.body.as[EmailVerification]))
    val response = verifySource.map(verify => emailService.sendVerifyEmail(verify)).via(serializeToJsonResponse).runWith(Sink.head)
    response.map( result => Results.Status(result._1)(result._2) )
  }
}
