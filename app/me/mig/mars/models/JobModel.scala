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
                 label: List[Short],
                 country: List[Int],
                 startTime: Timestamp,
                 endTime: Option[Timestamp],
                 interval: Long,
                 notificationType: String,
                 message: String,
                 callToAction: Map[String, String],
                 disabled: Option[Boolean] = None)
  case class NextJob(id: String, startTime: Timestamp)
  case class JobToken(userId: Int, username: Option[String], gcmToken: Option[String], iosToken: Option[Array[Byte]]) {
    var jobId: Option[String] = None
    def withJobId(jobId: String): JobToken = {
      this.jobId = Some(jobId)
      this
    }
  }

  /** Event models **/
  case class DispatchJob(jobId: String)

  /** Json models **/
  // Requests
  case class CreateJob(id: String, label: List[Short], country: List[Int], startTime: Long, endTime: Option[Long] = None, interval: Long = 60000, notificationType: String, message: String, callToAction: Map[String, String])
  // Responses
  case class CreateJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse
  case class GetJobsAck(data: List[Job], override val error: Option[String] = None) extends BaseResponse

  // Json Reads
  implicit val CreateJobReads = Json.reads[CreateJob]
  // Json Writes
  implicit val CreateJobAckWrites = Json.writes[CreateJobAck]
  implicit val JobsWrites = Json.writes[Job]
  implicit val GetJobsAckWrites = Json.writes[GetJobsAck]
}
