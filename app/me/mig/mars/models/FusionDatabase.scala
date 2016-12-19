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
//                                 mimeType: String,
                                 dateCreated: Timestamp,
                                 timeUpdated: Timestamp )

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
}

object NotificationMappings extends Enumeration {
  type NotificationMappings = Value

  val EMAIL_VERIFICATION = Value(72, "emailVerification")
  val FORGOT_PASSWORD_EMAIL = Value(74, "forgotPasswordEmail")
}