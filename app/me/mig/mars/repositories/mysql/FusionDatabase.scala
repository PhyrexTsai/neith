package me.mig.mars.repositories.mysql

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import me.mig.mars.models.NotificationModel.{GcmRegToken, IosDeviceToken, NotificationTemplate}
import me.mig.mars.models.UserModel.{User, UserId, UserLabel}
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.driver.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by jameshsiao on 9/2/16.
  */

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

  def getGcmRegToken(userId: Int): Future[Seq[GcmRegToken]] = {
    db.run(
      gcmRegTokens.filter( _.userId === userId ).result
    )
  }

  def updateGcmRegToken(userId: Int, originalToken: String, newToken: String) = {
    db.run(
      gcmRegTokens.filter( _.token === originalToken )
        .map(gToken => (gToken.token, gToken.dateLastUsed))
        .update( (newToken, new Timestamp(System.currentTimeMillis)) )
    )
  }

  def getIosDeviceToken(userId: Int): Future[Seq[IosDeviceToken]] = {
    db.run(
      iosDeviceTokens.filter( _.userId === userId ).result
    )
  }
  def updateIosDeviceToken(userId: Int, originalToken: Array[Byte], newToken: Array[Byte]) = {
    db.run(
      iosDeviceTokens.filter { _.deviceToken === originalToken }
        .map(gToken => (gToken.deviceToken, gToken.dateLastUsed))
        .update( (newToken, new Timestamp(System.currentTimeMillis)) )
    )
  }

  /**
    * User-related tables
    */
  private class UserIds(tag: Tag) extends Table[UserId](tag, "userid") {
    def id = column[Int]("Id")
    def username = column[String]("Username")

    def * = (id, username) <> (UserId.tupled, UserId.unapply)
  }
  private lazy val userIds = TableQuery[UserIds]

  private class Users(tag: Tag) extends Table[User](tag, "user") {
    def username = column[String]("Username")
    def countryId = column[Int]("CountryId")

    def * = (username, countryId) <> (User.tupled, User.unapply)
  }
  private lazy val users = TableQuery[Users]

  private class UserLabels(tag: Tag) extends Table[UserLabel](tag, "userlabel") {
    def userId = column[Int]("UserID")
    def labelType = column[Short]("Type")


    def * = (userId, labelType) <> (UserLabel.tupled, UserLabel.unapply)
  }
  private lazy val userLabels = TableQuery[UserLabels]

  /**
    * Implement the following SQL query with Slick
    * Query script:
    *     select A.userid, A.type, userid.username, user.countryid from
    *       (select * from userlabel where type in (List of label types)) A
    *       left outer join userid on A.userid=userid.id
    *       left outer join user on userid.username=user.username
    *       where countryid in (List of country IDs);
    *
    * @param usernames      List of user names to match, optional to labels and countries
    * @param labels     List of user labels to match, optional
    * @param countries  List of user countries to match, optional
    * @return           Seq[]
    */
  def getUserTokensByLabelAndCountry(usernames: Option[List[String]], labels: Option[List[Short]], countries: Option[List[Int]]): Future[Seq[(Option[Int], Option[String], Option[String], Option[Array[Byte]])]] = {
    val selectedUsers = usernames match {
      case Some(nameList) =>
        for {
          (user, userId) <- users.filter(_.username inSet nameList)
                                .joinLeft(userIds).on(_.username === _.username)
        } yield (userId.map(_.id), userId.map(_.username))
      case None => (labels, countries) match {
        case (Some(labelList), None) =>
          Logger.debug("Only label is set: " + labelList)
          for {
            ((label, userId), user) <- userLabels.filter(_.labelType inSet labelList)
                                                .joinLeft(userIds).on(_.userId === _.id)
                                                .joinLeft(users).on(_._2.map(_.username) === _.username)
          } yield (userId.map(_.id), user.map(_.username))
        case (None, Some(countryList)) =>
          Logger.debug("Only country is set: " + countryList)
          for {
            (user, userId) <- users.filter(_.countryId inSet countryList)
                                  .joinLeft(userIds).on(_.username === _.username)
          } yield (userId.map(_.id), userId.map(_.username))
        case (Some(labelList), Some(countryList)) =>
          Logger.debug("Both label and country are set: " + labelList + ", " + countryList)
          for {
            ((label, userId), userInfo) <- userLabels.filter(_.labelType inSet labelList)
                                                    .joinLeft(userIds).on(_.userId === _.id)
                                                    .joinLeft(users).on(_._2.map(_.username) === _.username)
                                                    .filter(_._2.map(_.countryId) inSet countryList)
          } yield (userId.map(_.id), userId.map(_.username))
        case _ =>
          Logger.warn("Either users nor labels and countries are not specified, no criteria to query")
          throw new IllegalArgumentException("No criteria specified, reject the query")
      }
    }

    val tokens = for {
      ((user, gcmtoken), iostoken) <- selectedUsers.joinLeft(gcmRegTokens).on(_._1 === _.userId)
                                                  .joinLeft(iosDeviceTokens).on(_._1._1 === _.userId)
    } yield (user._1, user._2, gcmtoken.map(_.token), iostoken.map(_.deviceToken))

    db.run(tokens.result)
  }
}

object NotificationMappings extends Enumeration {
  type NotificationMappings = Value

  val EMAIL_VERIFICATION = Value(72, "emailVerification")
  val FORGOT_PASSWORD_EMAIL = Value(74, "forgotPasswordEmail")
}