package me.mig.neith.controllers

import com.google.inject.Inject
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
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
                               )(implicit system: ActorSystem, mat: Materializer, ws: WSClient, ec: ExecutionContext) extends BaseController {

  def uploadImage(userId: Int) = Action.async(parse.multipartFormData) { request =>
    Source.fromFuture(imageService.uploadImage(userId, request.body))
      .map(processGeneralResponse[String])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def processGeneralResponse[T: TypeTag : Reads : Writes](result: String): Result = {
    Logger.info(s"IMAGE RESULT: ${result}")
    Ok(Json.obj("imageUrl" -> result)).as("application/json")
  }
}
