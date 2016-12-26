package me.mig.mars.models

import java.sql.Timestamp
import java.util.UUID

/**
  * Created by jameshsiao on 12/23/16.
  */
object JobModel {
  case class Job(id: UUID,
                 label: List[Short],
                 country: List[Int],
                 startTime: Timestamp,
                 endTime: Option[Timestamp],
                 interval: Long,
                 notificationType: String,
                 message: String,
                 callToAction: Map[String, String])
  case class NextJob(id: UUID, startTime: Timestamp)
  case class JobToken(userId: Int, username: Option[String], gcmToken: Option[String], iosToken: Option[Array[Byte]])
}
