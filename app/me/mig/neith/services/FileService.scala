package me.mig.neith.services

import java.util.Date

import awscala.DateTime
import com.google.inject.{Inject, Singleton}
import fly.play.s3._
import me.mig.neith.constants.ErrorCodes
import me.mig.neith.exceptions.NeithException
import me.mig.neith.models.Users._
import me.mig.neith.utils.ImageUtils
import org.joda.time.format.DateTimeFormat
import play.api.libs.Files
import play.api.libs.json.{JsValue, Json}
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by phyrextsai on 2017/1/19.
  */
@Singleton
class FileService @Inject()(ws: WSClient, config: Configuration, ec: ExecutionContext) {

  private val bucketName = config.getString("aws.s3.bucketName").getOrElse("images-staging.mig33.com")
  private val cdnDomain = config.getString("aws.s3.cdnDomain").getOrElse("b-img.cdn.mig.me")
  private val baseDomain = config.getString("aws.s3.baseDomain").getOrElse("s3-us-west-2.amazonaws.com")
  private val httpProtocol = config.getString("aws.s3.httpProtocol").getOrElse("http://")

  private val fileKey = "file"
  private val s3 = S3.fromConfiguration(ws, config)
  private val bucket = s3.getBucket(bucketName)
  private val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private val preSignedUrlExpire = 10

  /**
    * Upload single file
    *
    * @param userId
    * @param uploadFile
    * @return
    */
  def upload(userId: Int, uploadFile: MultipartFormData[Files.TemporaryFile]): Future[JsValue] = {
    import java.nio.file.{Files, Paths}
    uploadFile.file(fileKey)
      .filter(_.ref.file.length() > 0)
      .map(file => {
        ImageUtils.ALLOW_MIME_TYPE contains file.contentType.get match {
          case true => {
            val byteArray = Files.readAllBytes(Paths.get(file.ref.file.getPath))
            val name = ImageUtils.calculatePath(userId)
            val result = bucket + BucketFile(name, file.contentType.get, byteArray)
            result map { unit =>
              val fileUrl = httpProtocol + baseDomain + "/" + bucketName + "/" + name
              Json.obj(
                "fileUrl" -> fileUrl
              )
            }
          }
          case false => Future.failed(new NeithException(ErrorCodes.UNSUPPORTED_MIME_TYPE.errorCode, ErrorCodes.UNSUPPORTED_MIME_TYPE.message))
        }
      }).getOrElse({
        Future.failed(new NeithException(ErrorCodes.FILE_NOT_FOUND.errorCode, ErrorCodes.FILE_NOT_FOUND.message))
      })
  }

  /**
    * Generate Pre-signed url for S3 upload
    *
    * @param userId
    * @param file
    * @return
    */
  def preSignedUpload(userId: Int, file: PreSignedUpload): Future[JsValue] = {
    val imageUtils = new ImageUtils(config)
    Future.successful(
      Json.obj(
        "preSignedUrl" -> imageUtils.generatePreSignedUrl(bucketName, userId, file.fileName,  DateTime.now.plusMinutes(preSignedUrlExpire)).toString
      )
    )
  }

  /**
    * Initiate a multupart upload id to handle upload
    *
    * @param userId
    * @param file
    * @return
    */
  def initiateMultipartUpload(userId: Int, file: InitiateMultipartUpload): Future[JsValue] = {
    val result = bucket.initiateMultipartUpload(BucketFile(file.fileName, file.contentType))
    result map { bucketFileUploadTicket =>
      Json.obj(
        "fileName" -> bucketFileUploadTicket.name,
        "uploadId" -> bucketFileUploadTicket.uploadId
      )
    }
  }

  /**
    * Upload part of file
    *
    * @param userId
    * @param data
    * @return
    */
  def uploadPart(userId: Int, data: MultipartFormData[Files.TemporaryFile]): Future[JsValue] = {
    import java.nio.file.{Files, Paths}
    val formDataMap = data.dataParts
    data.file(fileKey).filter(_.ref.file.length() > 0) match {
      case Some(file) => {
        ImageUtils.ALLOW_MIME_TYPE contains file.contentType.get match {
          case true => {
            var dataPartNumber: Int = formDataMap ("dataPartNumber").head.toInt
            var fileName = formDataMap ("fileName").head
            var uploadId = formDataMap ("uploadId").head
            val byteArray = Files.readAllBytes (Paths.get (file.ref.file.getPath) )
            val result = bucket.uploadPart (new BucketFileUploadTicket (fileName, uploadId), new BucketFilePart (dataPartNumber, byteArray) )
            result map {
              bucketFilePartUploadTicket =>
              Logger.info ("UPLOAD_PART RESULT: " + bucketFilePartUploadTicket)
              Json.obj (
                "partNumber" -> bucketFilePartUploadTicket.partNumber,
                "eTag" -> bucketFilePartUploadTicket.eTag
                )
            }
          }
          case false => Future.failed(new NeithException(ErrorCodes.UNSUPPORTED_MIME_TYPE.errorCode, ErrorCodes.UNSUPPORTED_MIME_TYPE.message))
        }
      }
      case None => Future.failed(new NeithException(ErrorCodes.FILE_NOT_FOUND.errorCode, ErrorCodes.FILE_NOT_FOUND.message))
    }
  }

  /**
    * Complete upload, minimum size 5MB
    *
    * @param userId
    * @param data
    * @return
    */
  def completeMultipartUpload(userId: Int, data: CompleteMultipartUpload): Future[JsValue] = {
    println(s"${data.fileName}, ${data.uploadId}, ${data.partUploadTickets}")
    val result = bucket.completeMultipartUpload(
      new BucketFileUploadTicket(data.fileName, data.uploadId), data.partUploadTickets)
    result map { unit =>
      Json.obj("complete" -> true)
    }
  }

  /**
    * Abort upload
    *
    * @param userId
    * @param fileName
    * @param uploadId
    * @return
    */
  def abortMultipartUpload(userId: Int, fileName: String, uploadId: String): Future[JsValue] = {
    val result = bucket.abortMultipartUpload(
      new BucketFileUploadTicket(fileName, uploadId))
    result map { unit =>
      Json.obj("abort" -> true)
    }
  }

  /**
    * List all uploads
    *
    * @param userId
    * @param fileName
    * @param uploadId
    * @param maxUploads
    * @param delimiter
    * @return
    */
  def listMultipartUploads(userId: Int, fileName: String, uploadId: String, maxUploads: Int, delimiter: String): Future[JsValue] = {
    val acl = PUBLIC_READ
    val headers = (Map.empty).toList
    val result = s3.client
      .resourceRequest(bucketName, "")
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .withQueryString(
        "uploads" -> "",
        "delimiter" -> "",
        "max-uploads" -> maxUploads.toString,
        "key-marker" -> fileName,       // key => fileName
        "upload-id-marker" -> uploadId  // show files greater then this uploadId
      )
      .get

    result map S3Response { (status, response) =>
      import scala.collection.mutable.ListBuffer
      Logger.info("LIST_MULTIPART_UPLOADS STATUS: " + status + ", RESPONSE: " + response.xml)
      val xml = response.xml
      val multipartUploadList = ListBuffer[MultipartUpload]()
      multipartUploadList ++= (xml \ "Upload").map(n => {
        MultipartUpload(
          (n \ "Key").text,
          (n \ "UploadId").text,
          new Date(dateTimeFormat.parseMillis((n \ "Initiated").text))
        )
      })

      Json.toJson(multipartUploadList.toList)
    }
  }

  /**
    * List upload parts
    *
    * @param userId
    * @param fileName
    * @param uploadId
    * @param maxParts
    * @param partNumber
    * @return
    */
  def listParts(userId: Int, fileName: String, uploadId: String, maxParts: Int, partNumber: Int): Future[JsValue] = {
    val acl = PUBLIC_READ
    val headers = (Map.empty).toList
    val result = s3.client
      .resourceRequest(bucketName, fileName)
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .withQueryString(
        "uploadId" -> uploadId,
        "max-parts" -> maxParts.toString,
        "part-number-marker" -> partNumber.toString
      )
      .get

    result map S3Response { (status, response) =>
      import scala.collection.mutable.ListBuffer
      Logger.info("LIST_PARTS STATUS: " + status + ", RESPONSE: " + response.xml)
      val xml = response.xml
      val partList = ListBuffer[Part]()
      partList ++= (xml \ "Part").map(n => {
        Part(
          (n \ "PartNumber").text.toInt,
          (n \ "ETag").text,
          (n \ "Size").text.toDouble,
          new Date(dateTimeFormat.parseMillis((n \ "Initiated").text))
        )
      })

      Json.toJson(partList)
    }

  }
}
