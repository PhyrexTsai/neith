package me.mig.mars.models

import java.sql.Timestamp

import me.mig.mars.BaseResponse
import play.api.libs.json.Json

/**
  * Created by jameshsiao on 12/23/16.
  */
object NotificationModel {
  case class NotificationTemplate(
                                   notificationId: Option[Int],
                                   mapId: Option[Int],
                                   languageCode: String,
                                   templateType: Int,
                                   subjectTemplate: String,
                                   bodyTemplate: String,
                                   dateCreated: Timestamp,
                                   timeUpdated: Timestamp )
  case class GcmRegToken( userId: Int,
                          token: String,
                          dateLastUsed: Timestamp,
                          deviceId: Option[String],
                          clientVersion: Option[Int] )
  case class IosDeviceToken(userId: Int,
                            deviceToken: Array[Byte],
                            dateLastUsed: Timestamp,
                            deviceId: Option[String],
                            clientVersion: Option[Int] )
  case class GetNotificationTypesAck(data: List[String], override val error: Option[String] = None) extends BaseResponse

  implicit val GetNotificationTypesAckWrites = Json.writes[GetNotificationTypesAck]
}
