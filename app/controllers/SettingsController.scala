package controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import play.api.mvc.Action
import play.api.mvc.BodyParsers.parse
import play.api.mvc.Results.{BadRequest, Ok}
import services.EmailService
import services.EmailService.{ForgotPassword, serializeToJsonResponse}

import scala.collection.immutable.Iterable

/**
  * Created by jameshsiao on 9/2/16.
  */
@Singleton
class SettingsController @Inject()(system: ActorSystem, emailService: EmailService) {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val materializer = ActorMaterializer()(system)

  def handleForgotPassword = Action.async(parse.json) { request =>
    val forgotPasswordSource = Source[ForgotPassword](Iterable(request.body.as[ForgotPassword]))
    val response = forgotPasswordSource.map(forgot => emailService.sendForgotPasswordEmail(forgot)).via(serializeToJsonResponse).runWith(Sink.head)
    response.map(result =>
      if (result._1 == 200) {
        Ok(result._2)
      } else {
        BadRequest(result._2)
      }
    )
  }
}
