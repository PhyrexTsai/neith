package me.mig.mars.models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.driver.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by jameshsiao on 9/2/16.
  */
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

@Singleton
class FusionDatabase @Inject()(@NamedDatabase("fusion") dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import NotificationMappings._
  import dbConfig._
  import driver.api._

  private class NotificationTemplates(tag: Tag) extends Table[NotificationTemplate](tag, "notificationtemplate") {
    def notificationId = column[Int]("NotificationId")
    def mapId = column[Int]("MapId")
    def languageCode = column[String]("LanguageCode")
    def templateType = column[Int]("TemplateType")
    def subjectTemplate = column[String]("SubjectTemplate")
    def bodyTemplate = column[String]("BodyTemplate")
//    def mimeType = column[String]("MimeType")
    def dateCreated = column[Timestamp]("DateCreated")
    def timeUpdated = column[Timestamp]("TimeUpdated")

    def * = (notificationId.?, mapId.?, languageCode, templateType, subjectTemplate, bodyTemplate, dateCreated, timeUpdated) <>
      (NotificationTemplate.tupled, NotificationTemplate.unapply)
  }

  private lazy val templates = TableQuery[NotificationTemplates]

  def getTemplateByMapId(mapId: NotificationMappings): Future[Seq[NotificationTemplate]] = {
    db.run(templates.filter(t => t.mapId === mapId.id).result)
  }

  /**
    * Device Token tables
    */
  private class GcmRegTokens(tag: Tag) extends Table[GcmRegToken](tag, "gcmregtoken") {
    def userId = column[Int]("UserID")
    def token = column[String]("Token")
    def dateLastUsed = column[Timestamp]("DateLastUsed")
    def deviceId = column[String]("DeviceId")
    def clientVersion = column[Int]("ClientVersion")

    def * = (userId, token, dateLastUsed, deviceId.?, clientVersion.?) <>
      (GcmRegToken.tupled, GcmRegToken.unapply)
  }
  private lazy val gcmRegTokens = TableQuery[GcmRegTokens]

  private class IosDeviceTokens(tag: Tag) extends Table[IosDeviceToken](tag, "iosdevicetoken") {
    def userId = column[Int]("UserID")
    def deviceToken = column[Array[Byte]]("DeviceToken")
    def dateLastUsed = column[Timestamp]("DateLastUsed")
    def deviceId = column[String]("DeviceId")
    def clientVersion = column[Int]("ClientVersion")

    def * = (userId, deviceToken, dateLastUsed, deviceId.?, clientVersion.?) <>
      (IosDeviceToken.tupled, IosDeviceToken.unapply)
  }
  private lazy val iosDeviceTokens = TableQuery[IosDeviceTokens]

  def updateGcmRegToken(userId: Int, originalToken: String, newToken: String) = {
    db.run(
      gcmRegTokens.filter( _.token === originalToken)
        .map(gToken => (gToken.token, gToken.dateLastUsed))
        .update( (newToken, new Timestamp(System.currentTimeMillis)) )
    )
  }

  def getIosDeviceToken(userId: Int): Future[Seq[IosDeviceToken]] = {
    db.run(
      iosDeviceTokens.filter ( _.userId === userId).result
    )
  }
  def updateIosDeviceToken(userId: Int, originalToken: Array[Byte], newToken: Array[Byte]) = {
    db.run(
      iosDeviceTokens.filter { _.deviceToken === originalToken }
        .map(gToken => (gToken.deviceToken, gToken.dateLastUsed))
        .update( (newToken, new Timestamp(System.currentTimeMillis)) )
    )
  }
}

object NotificationMappings extends Enumeration {
  type NotificationMappings = Value

  val EMAIL_VERIFICATION = Value(72, "emailVerification")
  val FORGOT_PASSWORD_EMAIL = Value(74, "forgotPasswordEmail")
}