package me.mig.mars.models

import java.sql.Timestamp

import me.mig.mars.BaseResponse
import org.joda.time.DateTime
import play.api.libs.json._

/**
  * Created by jameshsiao on 12/23/16.
  */
object JobModel {
  /** Table models **/
  case class Job(id: String,
                 creator: String,
                 users: Option[List[String]] = None,
                 label: Option[List[Short]] = None,
                 country: Option[List[Int]] = None,
                 startTime: Timestamp,
                 endTime: Option[Timestamp],
                 interval: Option[Long],
                 notificationType: String,
                 message: String,
                 callToAction: Map[String, String],
                 createdTime: Timestamp,
                 disabled: Option[Boolean] = None)
  case class NextJob(id: String, startTime: Timestamp)
  // History
  case class JobHistory(id: String,
                        creator: String,
                        users: Option[List[String]] = None,
                        label: Option[List[Short]] = None,
                        country: Option[List[Int]] = None,
                        startTime: Timestamp,
                        endTime: Option[Timestamp],
                        interval: Option[Long],
                        notificationType: String,
                        message: String,
                        callToAction: Map[String, String],
                        createdTime: Timestamp,
                        totalCount: Long)
  case class JobHistoryDetail(id: String,
                              startTime: Timestamp,
                              user: String,
                              success: Boolean,
                              platform: String,
                              deviceToken: String,
                              endpoint: Option[String],
                              reason: String)
  case class JobHistorySuccess(id: String,
                               startTime: Timestamp,
                               successCount: Long,
                               failureCount: Long)
  case class JobFullHistory(id: String,
                            creator: String,
                            users: Option[List[String]] = None,
                            label: Option[List[Short]] = None,
                            country: Option[List[Int]] = None,
                            startTime: Timestamp,
                            endTime: Option[Timestamp],
                            interval: Option[Long],
                            notificationType: String,
                            message: String,
                            callToAction: Map[String, String],
                            createdTime: Timestamp,
                            totalCount: Long,
                            successCount: Long,
                            failureCount: Long,
                            detail: List[JobHistoryDetail])

  /** Event models **/
  case class ScheduleJob(jobId: String)
  case class DispatchJob(jobId: String)
  case class PushJob(jobId: String, startTime: Timestamp, userId: Int, message: String, callToAction: Option[Map[String, String]], username: Option[String], gcmToken: Option[String], iosToken: Option[Array[Byte]])

  /** Json models **/
  // Requests
  case class CreateUpdateJob(id: String,
                             creator: String,
                             users: Option[List[String]] = None,
                             label: Option[List[Short]] = None,
                             country: Option[List[Int]] = None,
                             startTime: Long,
                             endTime: Option[Long] = None,
                             interval: Option[Long],
                             notificationType: String,
                             message: String,
                             callToAction: Map[String, String])
  // Responses
  case class CreateUpdateJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse
  case class GetJobsAck(data: List[Job], override val error: Option[String] = None) extends BaseResponse
  case class GetJobHistoryAck(data: List[JobFullHistory], override val error: Option[String] = None) extends BaseResponse
  case class DeleteJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse

  // Json Reads
  implicit val CreateJobReads = Json.reads[CreateUpdateJob]
  implicit val timestampFormat = new Format[Timestamp] {
    def reads(json: JsValue): JsResult[Timestamp] = json match {
      case JsNumber(bigDecimal) =>
        JsSuccess(new Timestamp(bigDecimal.toLong))
      case JsString(txtFormat) =>
        JsSuccess(new Timestamp(DateTime.parse(txtFormat).getMillis))
      case _ =>
        JsError(s"Wrong timestamp format: $json")
    }

    def writes(o: Timestamp): JsValue = JsNumber(o.getTime)
  }
  implicit val PushJobReads = Json.reads[PushJob]
  // Json Writes
  implicit val CreateJobAckWrites = Json.writes[CreateUpdateJobAck]
  implicit val JobsWrites = Json.writes[Job]
  implicit val GetJobsAckWrites = Json.writes[GetJobsAck]
  implicit val JobHistoryDetailWrites = Json.writes[JobHistoryDetail]
  implicit val JobFullHistoryWrites = Json.writes[JobFullHistory]
  implicit val GetJobHistoryAckWrites = Json.writes[GetJobHistoryAck]
  implicit val DeleteJobAckWrites = Json.writes[DeleteJobAck]
  implicit val PushJobWrites = Json.writes[PushJob]
}
