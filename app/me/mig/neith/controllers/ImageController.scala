package me.mig.neith.controllers

import com.google.inject.Inject
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.neith.constants.ErrorCodes._
import me.mig.neith.exceptions.NeithException
import me.mig.neith.services.ImageService
import play.api.libs.json.{Json, Reads, Writes}
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._

/**
  * Created by phyrextsai on 2017/1/19.
  */
class ImageController @Inject()(imageService: ImageService, config: Configuration
                               )(implicit system: ActorSystem, mat: Materializer, ws: WSClient, ec: ExecutionContext) extends Controller {

  def uploadImage(userId: Int) = Action.async(parse.multipartFormData) { request =>
//    import scala.concurrent.ExecutionContext.Implicits.global
//    request.body.file("file").map { file =>
//      val s3 = S3.fromConfiguration(ws, config)
//      val bucket = s3.getBucket("")
//      val byteArray = Files.readAllBytes(Paths.get(file.ref.file.getPath))
//      val result = bucket + BucketFile(file.filename, file.contentType.get, byteArray)
//
//      result map { unit =>
//        Logger.info("AWS S3 upload: " + unit.toString)
//        Ok("uploaded")
//      }
//    }.getOrElse {
//      scala.concurrent.Future {
//        Ok("error")
//      }
//    }
    Source.fromFuture(imageService.uploadImage(userId, request.body))
      .map(processGeneralResponse[String])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def processGeneralResponse[T: TypeTag : Reads : Writes](result: Unit): Result = {
    Ok(Json.obj("isSuccess" -> true)).as("application/json")
  }

  def processErrorResponse: PartialFunction[Throwable, Result] = {
    case ex: Throwable =>
      Logger.error("Exception occurred:", ex)
      ex match {
          case _: NeithException =>
            BadRequest(Json.obj("error" -> Json.obj(
              "errno" -> Json.toJson(ex.asInstanceOf[NeithException].errorCode),
              "message" -> Json.toJson(ex.getMessage)
            )))
      }
  }
}
