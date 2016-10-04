package me.mig.mars.controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.services.EmailService
import me.mig.mars.services.EmailService._
import play.api.libs.json.{JsError, JsResultException, JsSuccess}
import play.api.mvc.BodyParsers.parse
import play.api.mvc.{Action, Results}

/**
  * Created by jameshsiao on 9/2/16.
  */
@Singleton
class SettingsController @Inject()(emailService: EmailService)(implicit system: ActorSystem, materializer: Materializer) {

  def handleForgotPassword = Action.async(parse.json) { request =>
    val forgotPasswordSource = Source.single[ForgotPassword](request.body.as[ForgotPassword])
    forgotPasswordSource.map(forgot => emailService.sendForgotPasswordEmail(forgot))
      .via(serializeToJsonResponse)
      .map( result => Results.Status(result._1)(result._2) )
      .runWith(Sink.head)
//    response
  }
}
