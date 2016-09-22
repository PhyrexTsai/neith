package me.mig.mars.controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.services.EmailService
import me.mig.mars.services.EmailService._
import play.api.mvc.BodyParsers.parse
import play.api.mvc.{Action, Results}

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
    response.map( result => Results.Status(result._1)(result._2) )
  }
}
