package me.mig.mars.models

import java.sql.Timestamp

import me.mig.mars.BaseResponse
import play.api.libs.json.Json

/**
  * Created by jameshsiao on 12/23/16.
  */
object JobModel {
  /** Table models **/
  case class Job(id: String,
                 creator: String,
                 label: List[Short],
                 country: List[Int],
                 startTime: Timestamp,
                 endTime: Option[Timestamp],
                 interval: Option[Long],
                 notificationType: String,
                 message: String,
                 callToAction: Map[String, String],
                 createdTime: Timestamp,
                 disabled: Option[Boolean] = None)
  case class NextJob(id: String, startTime: Timestamp)

  /** Event models **/
  case class ScheduleJob(jobId: String, delay: Long)
  case class DispatchJob(jobId: String)
  case class PushJob(jobId: String, userId: Int, message: String, callToAction: Option[Map[String, String]], username: Option[String], gcmToken: Option[String], iosToken: Option[Array[Byte]])

  /** Json models **/
  // Requests
  case class CreateJob(id: String, creator: String, label: List[Short], country: List[Int], startTime: Long, endTime: Option[Long] = None, interval: Option[Long], notificationType: String, message: String, callToAction: Map[String, String])
  // Responses
  case class CreateJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse
  case class GetJobsAck(data: List[Job], override val error: Option[String] = None) extends BaseResponse

  // Json Reads
  implicit val CreateJobReads = Json.reads[CreateJob]
  implicit val PushJobReads = Json.reads[PushJob]
  // Json Writes
  implicit val CreateJobAckWrites = Json.writes[CreateJobAck]
  implicit val JobsWrites = Json.writes[Job]
  implicit val GetJobsAckWrites = Json.writes[GetJobsAck]
  implicit val PushJobWrites = Json.writes[PushJob]
}
