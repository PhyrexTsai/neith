package me.mig.mars

import javax.inject.{Inject, Provider, Singleton}

import play.api.http.DefaultHttpErrorHandler
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.mvc.{BodyParsers, RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment, Logger, OptionalSourceMapper}

import scala.concurrent.Future

/**
  * Created by jameshsiao on 10/4/16.
  */
@Singleton
class ErrorHandler @Inject()(
                              env: Environment,
                              config: Configuration,
                              sourceMapper: OptionalSourceMapper,
                              router: Provider[Router]
                            ) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
  case class ErrorResponse(error: String)
  implicit val errorResponseWrites = Json.writes[ErrorResponse]

  def toJson(message: String) =
    Json.toJson(ErrorResponse(message))

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    exception match {
      case ex: JsResultException =>
        Logger.warn("Invalid input parameters: " + ex.getMessage)
        Future.successful(BadRequest(toJson("Invalid input parameters")))
      case x: Throwable => super.onServerError(request, exception)
    }
  }
}

object ErrorHandler {
  import scala.concurrent.ExecutionContext.Implicits.global

  def validateJson[T: Reads]= BodyParsers.parse.json.validate(
    _.validate[T].asEither.left.map(e => {
      Logger.info("validateJson: " + e)
      BadRequest(JsError.toJson(e))
    })
  )
}