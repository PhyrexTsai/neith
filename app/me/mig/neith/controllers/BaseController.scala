package me.mig.neith.controllers

import fly.play.s3.S3Exception
import me.mig.neith.constants.ErrorCodes
import me.mig.neith.exceptions.NeithException
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.mvc._

import scala.reflect.runtime.universe._

/**
  * Created by phyrextsai on 2017/2/10.
  */
trait BaseController extends Controller {

  def processGeneralResponse[T: TypeTag : Reads : Writes](result: JsValue): Result = {
    Logger.info(s"RESULT: ${result}")
    Ok(result).as("application/json")
  }

  def processErrorResponse: PartialFunction[Throwable, Result] = {
    case ex: Throwable =>
      Logger.error("Exception occurred:", ex)
      ex match {
        case e: NeithException =>
          BadRequest(Json.obj("error" -> Json.obj(
            "errno" -> Json.toJson(e.errorCode),
            "message" -> Json.toJson(e.getMessage)
          )))
        case e: S3Exception =>
          BadRequest(Json.obj("error" -> Json.obj(
            "errno" -> Json.toJson(e.code),
            "message" -> Json.toJson(e.getMessage)
          )))
        case _: Exception =>
          InternalServerError(Json.obj("error" -> Json.obj(
            "errno" -> Json.toJson(ErrorCodes.UNKNOWN_ERROR.errorCode),
            "message" -> Json.toJson(ErrorCodes.UNKNOWN_ERROR.message)
          )))
      }
  }

}
