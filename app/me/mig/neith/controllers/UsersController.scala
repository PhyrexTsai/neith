package me.mig.neith.controllers

import com.google.inject.Inject
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.neith.models.Users._
import me.mig.neith.services.FileService
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
  * Created by phyrextsai on 2017/1/19.
  */
class UsersController @Inject()(fileService: FileService)(
  implicit system: ActorSystem, mat: Materializer, ws: WSClient, ec: ExecutionContext) extends BaseController {

  def upload(userId: Int) = Action.async(parse.multipartFormData) { request =>
    Source.fromFuture(fileService.upload(userId, request.body))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def initiateMultipartUpload(userId: Int) = Action.async(parse.json) { request =>
    Source.single(request.body.as[InitiateMultipartUpload])
      .mapAsync(1)(fileService.initiateMultipartUpload(userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def uploadPart(userId: Int) = Action.async(parse.multipartFormData) { request =>
    Source.fromFuture(fileService.uploadPart(userId, request.body))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def completeMultipartUpload(userId: Int) = Action.async(parse.json) { request =>
    Source.single(request.body.as[CompleteMultipartUpload])
      .mapAsync(1)(fileService.completeMultipartUpload(userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def abortMultipartUpload(userId: Int, fileName: String, uploadId: String) = Action.async { request =>
    Source.fromFuture(fileService.abortMultipartUpload(userId, fileName, uploadId))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def listMultipartUploads(userId: Int, fileName: String, uploadId: String, maxUploads: Int, delimiter: String) = Action.async { request =>
    Source.fromFuture(fileService.listMultipartUploads(userId, fileName, uploadId, maxUploads, delimiter))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def listParts(userId: Int, fileName: String, uploadId: String, maxParts: Int, partNumber: Int) = Action.async { request =>
    Source.fromFuture(fileService.listParts(userId, fileName, uploadId, maxParts, partNumber))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }
}
