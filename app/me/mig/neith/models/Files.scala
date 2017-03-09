package me.mig.neith.models

import java.util.Date

import fly.play.s3.BucketFilePartUploadTicket
import play.api.libs.json.Json

/**
  * Created by phyrextsai on 2017/2/14.
  */
object Files {

  case class UploadResp(fileUrl: String)

  case class PreSignedUpload(fileName: String)

  case class PreSignedPartUpload(fileName: String, partNumber: Int, uploadId: String)

  case class PreSignedUploadResp(preSignedUrl: String)

  case class InitiateMultipartUpload(fileName: String, contentType: String)

  case class InitiateMultipartUploadResp(fileName: String, uploadId: String)

  case class UploadTicket(fileName: String, uploadId: String)

  case class MultipartUpload(fileName: String, uploadId: String, initiated: Date)

  case class Part(partNumber: Int, eTag: String, size: Double, lastModified: Date)

  case class CompleteMultipartUpload(fileName: String, uploadId: String, partUploadTickets: List[BucketFilePartUploadTicket])

  implicit val uploadRespWrites = Json.writes[UploadResp]
  implicit val uploadRespReads = Json.reads[UploadResp]
  implicit val preSignedUploadWrites = Json.writes[PreSignedUpload]
  implicit val preSignedUploadReads = Json.reads[PreSignedUpload]
  implicit val preSignedPartUploadWrites = Json.writes[PreSignedPartUpload]
  implicit val preSignedPartUploadReads = Json.reads[PreSignedPartUpload]
  implicit val preSignedUploadRespWrites = Json.writes[PreSignedUploadResp]
  implicit val preSignedUploadRespReads = Json.reads[PreSignedUploadResp]
  implicit val initiateMultipartUploadWrites = Json.writes[InitiateMultipartUpload]
  implicit val initiateMultipartUploadReads = Json.reads[InitiateMultipartUpload]
  implicit val initiateMultipartUploadRespWrites = Json.writes[InitiateMultipartUploadResp]
  implicit val initiateMultipartUploadRespReads = Json.reads[InitiateMultipartUploadResp]
  implicit val bucketFilePartUploadTicketWrites = Json.writes[BucketFilePartUploadTicket]
  implicit val bucketFilePartUploadTicketReads = Json.reads[BucketFilePartUploadTicket]
  implicit val uploadTicketWrites = Json.writes[UploadTicket]
  implicit val uploadTicketReads = Json.reads[UploadTicket]
  implicit val multipartUploadWrites = Json.writes[MultipartUpload]
  implicit val multipartUploadReads = Json.reads[MultipartUpload]
  implicit val partWrites = Json.writes[Part]
  implicit val partReads = Json.reads[Part]
  implicit val completeMultipartUploadWrites = Json.writes[CompleteMultipartUpload]
  implicit val completeMultipartUploadReads = Json.reads[CompleteMultipartUpload]
}
