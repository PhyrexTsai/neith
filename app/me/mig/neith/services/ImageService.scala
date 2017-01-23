package me.mig.neith.services

import com.google.inject.Inject
import fly.play.s3.{BucketFile, S3, S3Exception}
import me.mig.neith.constants.ErrorCodes
import me.mig.neith.exceptions.NeithException
import play.api.libs.Files
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by phyrextsai on 2017/1/19.
  */
class ImageService @Inject()(ws: WSClient, config: Configuration, ec: ExecutionContext) {

  private val bucketName = config.getString("aws.s3.bucketName").getOrElse("")

  def uploadImage(userId: Int, imageFile: MultipartFormData[Files.TemporaryFile]): Future[Unit] = {
    import java.nio.file.{Files, Paths}
    val s3 = S3.fromConfiguration(ws, config)
    val bucket = s3.getBucket(bucketName)
    imageFile.file("file").map(file => {
      val byteArray = Files.readAllBytes(Paths.get(file.ref.file.getPath))
      val result = bucket + BucketFile(file.filename, file.contentType.get, byteArray)
      result
    }).getOrElse(
      Future.failed(new NeithException(ErrorCodes.FILE_NOT_FOUND.errorCode, ErrorCodes.FILE_NOT_FOUND.message))
    )
  }
}
