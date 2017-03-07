package me.mig.neith.controllers

import com.google.inject.Inject
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.neith.models.Files._
import me.mig.neith.services.FileService
import me.mig.solar.sessioncheck.actions.{SessionAuthAction, SessionIdAuthOnlyAction}
import me.mig.solar.sessioncheck.dao.SessionInfoDAO
import me.mig.solar.sessioncheck.services.UserAuthService
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

/**
  * Created by phyrextsai on 2017/1/19.
  */
class FilesController @Inject()(fileService: FileService,
                                val sessionAuthAction: SessionAuthAction,
                                val sessionIdAuthOnlyAction: SessionIdAuthOnlyAction,
                                val userAuthService: UserAuthService,
                                val sessionInfoDAO: SessionInfoDAO)
                               (implicit val system: ActorSystem,
                                val mat: Materializer, ws: WSClient) extends BaseController {

  def preSignedUpload = VerifiedUserAction.async(parse.json) { request =>
    Source.single(request.body.as[PreSignedUpload])
      .mapAsync(1)(fileService.preSignedUpload(request.userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def upload = VerifiedUserAction.async(parse.multipartFormData) { request =>
    Source.fromFuture(fileService.upload(request.userId, request.body))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def initiateMultipartUpload = VerifiedUserAction.async(parse.json) { request =>
    Source.single(request.body.as[InitiateMultipartUpload])
      .mapAsync(1)(fileService.initiateMultipartUpload(request.userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def uploadPart = VerifiedUserAction.async(parse.multipartFormData) { request =>
    Source.fromFuture(fileService.uploadPart(request.userId, request.body))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def completeMultipartUpload = VerifiedUserAction.async(parse.json) { request =>
    Source.single(request.body.as[CompleteMultipartUpload])
      .mapAsync(1)(fileService.completeMultipartUpload(request.userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def abortMultipartUpload(fileName: String, uploadId: String) = VerifiedUserAction.async { request =>
    Source.fromFuture(fileService.abortMultipartUpload(request.userId, fileName, uploadId))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def listMultipartUploads(fileName: String, uploadId: String, maxUploads: Int, delimiter: String) = VerifiedUserAction.async { request =>
    Source.fromFuture(fileService.listMultipartUploads(request.userId, fileName, uploadId, maxUploads, delimiter))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def listParts(fileName: String, uploadId: String, maxParts: Int, partNumber: Int) = VerifiedUserAction.async { request =>
    Source.fromFuture(fileService.listParts(request.userId, fileName, uploadId, maxParts, partNumber))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }
}
