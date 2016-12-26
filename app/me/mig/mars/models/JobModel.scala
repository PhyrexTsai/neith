package me.mig.mars.models

import java.sql.Timestamp

/**
  * Created by jameshsiao on 12/23/16.
  */
object JobModel {
  case class Job(id: String,
                 label: List[Short],
                 country: List[Int],
                 startTime: Timestamp,
                 endTime: Option[Timestamp],
                 interval: Long,
                 notificationType: String,
                 message: String,
                 callToAction: Map[String, String])
  case class NextJob(id: String, startTime: Timestamp)
  case class DispatchJob(jobId: String)
  case class JobToken(userId: Int, username: Option[String], gcmToken: Option[String], iosToken: Option[Array[Byte]])
}
