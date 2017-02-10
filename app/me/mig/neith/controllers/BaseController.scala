package me.mig.neith.controllers

import me.mig.neith.exceptions.NeithException
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import fly.play.s3.S3Exception

/**
  * Created by phyrextsai on 2017/2/10.
  */
trait BaseController extends Controller {
  def processErrorResponse: PartialFunction[Throwable, Result] = {
    case ex: Throwable =>
      Logger.error("Exception occurred:", ex)
      ex match {
        case e: NeithException =>
          BadRequest(Json.obj("error" -> Json.obj(
            "errno" -> Json.toJson(e.errorCode),
            "message" -> Json.toJson(e.getMessage)
          )))
        case _: Exception =>
          InternalServerError
      }
  }
}
