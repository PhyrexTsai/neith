import javax.inject.{Inject, Provider, Singleton}

import play.api.http.{DefaultHttpErrorHandler, HttpErrorHandler}
import play.api.libs.json.{JsResultException, Json}
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment, OptionalSourceMapper}

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
      case ex: JsResultException => Future.successful(BadRequest(toJson("Invalid input parameters")))
      case _ => Future.successful(InternalServerError(toJson("A server error occurred: " + exception.getMessage)))
    }
  }
}