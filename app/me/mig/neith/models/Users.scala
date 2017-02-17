package me.mig.neith.models

import java.util.Date

import play.api.libs.json.Json

/**
  * Created by phyrextsai on 2017/2/14.
  */
object Users {

  case class UploadResp(fileUrl: String)

  case class InitiateMultipartUpload(fileName: String, contentType: String)

  case class InitiateMultipartUploadResp(fileName: String, uploadId: String)

  case class PartUploadTicket(partNumber: Int, eTag: String)

  case class UploadTicket(fileName: String, uploadId: String)

  case class MultipartUpload(fileName: String, uploadId: String, initiated: Date)

  case class Part(partNumber: Int, eTag: String, size: Double, lastModified: Date)

  case class CompleteMultipartUpload(bucketName: String, uploadTicket: UploadTicket, partUploadTickets: List[PartUploadTicket])

  implicit val uploadRespWrites = Json.writes[UploadResp]
  implicit val uploadRespReads = Json.reads[UploadResp]
  implicit val initiateMultipartUploadWrites = Json.writes[InitiateMultipartUpload]
  implicit val initiateMultipartUploadReads = Json.reads[InitiateMultipartUpload]
  implicit val initiateMultipartUploadRespWrites = Json.writes[InitiateMultipartUploadResp]
  implicit val initiateMultipartUploadRespReads = Json.reads[InitiateMultipartUploadResp]
  implicit val partUploadTicketWrites = Json.writes[PartUploadTicket]
  implicit val partUploadTicketReads = Json.reads[PartUploadTicket]
  implicit val uploadTicketWrites = Json.writes[UploadTicket]
  implicit val uploadTicketReads = Json.reads[UploadTicket]
  implicit val multipartUploadWrites = Json.writes[MultipartUpload]
  implicit val multipartUploadReads = Json.reads[MultipartUpload]
  implicit val partWrites = Json.writes[Part]
  implicit val partReads = Json.reads[Part]
  implicit val completeMultipartUploadWrites = Json.writes[CompleteMultipartUpload]
  implicit val completeMultipartUploadReads = Json.reads[CompleteMultipartUpload]
}
