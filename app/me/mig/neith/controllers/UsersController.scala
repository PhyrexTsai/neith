package me.mig.neith.controllers

import com.google.inject.Inject
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.neith.models.Users._
import me.mig.neith.services.FileService
import me.mig.solar.sessioncheck.actions.{SessionAuthAction, SessionIdAuthOnlyAction}
import me.mig.solar.sessioncheck.dao.SessionInfoDAO
import me.mig.solar.sessioncheck.services.UserAuthService
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

/**
  * Created by phyrextsai on 2017/1/19.
  */
class UsersController @Inject()(fileService: FileService,
                                val sessionAuthAction: SessionAuthAction,
                                val sessionIdAuthOnlyAction: SessionIdAuthOnlyAction,
                                val userAuthService: UserAuthService,
                                val sessionInfoDAO: SessionInfoDAO)
                               (implicit val system: ActorSystem,
                                val mat: Materializer, ws: WSClient) extends BaseController {

  def preSignedUpload(userId: Int) = VerifiedUserAction(userId).async(parse.json) { request =>
    Source.single(request.body.as[PreSignedUpload])
      .mapAsync(1)(fileService.preSignedUpload(userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def upload(userId: Int) = VerifiedUserAction(userId).async(parse.multipartFormData) { request =>
    Source.fromFuture(fileService.upload(userId, request.body))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def initiateMultipartUpload(userId: Int) = VerifiedUserAction(userId).async(parse.json) { request =>
    Source.single(request.body.as[InitiateMultipartUpload])
      .mapAsync(1)(fileService.initiateMultipartUpload(userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def uploadPart(userId: Int) = VerifiedUserAction(userId).async(parse.multipartFormData) { request =>
    Source.fromFuture(fileService.uploadPart(userId, request.body))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def completeMultipartUpload(userId: Int) = VerifiedUserAction(userId).async(parse.json) { request =>
    Source.single(request.body.as[CompleteMultipartUpload])
      .mapAsync(1)(fileService.completeMultipartUpload(userId, _))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def abortMultipartUpload(userId: Int, fileName: String, uploadId: String) = VerifiedUserAction(userId).async { request =>
    Source.fromFuture(fileService.abortMultipartUpload(userId, fileName, uploadId))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def listMultipartUploads(userId: Int, fileName: String, uploadId: String, maxUploads: Int, delimiter: String) = VerifiedUserAction(userId).async { request =>
    Source.fromFuture(fileService.listMultipartUploads(userId, fileName, uploadId, maxUploads, delimiter))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }

  def listParts(userId: Int, fileName: String, uploadId: String, maxParts: Int, partNumber: Int) = VerifiedUserAction(userId).async { request =>
    Source.fromFuture(fileService.listParts(userId, fileName, uploadId, maxParts, partNumber))
      .map(processGeneralResponse[JsValue])
      .runWith(Sink.head)
      .recover(processErrorResponse)
  }
}
