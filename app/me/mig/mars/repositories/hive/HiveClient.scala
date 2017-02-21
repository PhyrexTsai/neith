package me.mig.mars.repositories.hive

import java.sql._
import javax.inject.Inject

import play.api.{Configuration, Logger}

import scala.collection.mutable

/**
  * Created by jameshsiao on 2/8/17.
  */
class HiveClient @Inject()(configuration: Configuration) {
  private final val MAX_FETCH_SIZE = 10000
  private final val SELECT_SCHEDULED_JOB_USERS = "select id, username from dm.dim_user where username in (?)"
  private final val SELECT_SCHEDULED_JOB_USERS_BY_LABEL = "select a.id userid, a.username from dm.dim_user a join ds.fct_user_label b on b.type in (?) and a.id = b.userid"
  private final val SELECT_SCHEDULED_JOB_USERS_BY_COUNTRY = "select id, username from dm.dim_user where countryid in (?)"
  private final val SELECT_SCHEDULED_JOB_USERS_BY_LABEL_OR_COUNTRY = "select a.id userid, b.type, a.username, a.countryid from dm.dim_user a join ds.fct_user_label b on a.countryid in (?) and b.type in (?) and a.id = b.userid"
  private val config = configuration.underlying.getConfig("hive")

  val isExist: Boolean = config.getString("jdbcUrl") != null && config.getString("jdbcUrl") != ""

  def getScheduledJobUsers(users: Option[List[String]], labels: Option[List[Short]], countries: Option[List[Int]]) = {
    Class.forName(config.getString("driver"))
    val conn = DriverManager.getConnection(config.getString("jdbcUrl"), config.getString("user"), config.getString("password"))

    val preparedSql: String = users match {
      case Some(userList) =>
        SELECT_SCHEDULED_JOB_USERS.replace("?", userList.mkString(","))
      case None => (labels, countries) match {
        case (Some(labelList), None) =>
          SELECT_SCHEDULED_JOB_USERS_BY_LABEL.replace("?", labelList.mkString(","))
        case (None, Some(countryList)) =>
          SELECT_SCHEDULED_JOB_USERS_BY_COUNTRY.replace("?", countryList.mkString(","))
        case (Some(labelList), Some(countryList)) =>
          SELECT_SCHEDULED_JOB_USERS_BY_LABEL_OR_COUNTRY.replaceFirst("\\?", countries.mkString(",")).replace("?", labels.mkString(","))
        case _ =>
          Logger.error("Either users nor labels and countries are not specified, no criteria to query")
          throw new IllegalArgumentException("No criteria specified, reject the query")
      }
    }
    Logger.debug("preparedSql: " + preparedSql)
    val stmt: PreparedStatement = conn.prepareStatement(preparedSql)
    try {
      stmt.setFetchSize(MAX_FETCH_SIZE)
      val res = stmt.executeQuery()
      var resultList = mutable.ListBuffer[(Int, String)]()
      while (res.next()) {
        resultList += ( (res.getInt(1), res.getString(2)) )
      }
      Logger.debug("resultList: " + resultList)
      resultList.toList
    } catch {
      case ex: SQLException =>
        Logger.error("SQLException: " + ex.getMessage)
        List()
      case ex: SQLFeatureNotSupportedException =>
        Logger.error("SQLFeatureNotSupportedException: " + ex.getMessage)
        List()
      case ex: Throwable =>
        Logger.error("Query hive encounters error: " + ex.getMessage)
        Logger.error("Exception: " + ex.printStackTrace())
        List()
    } finally {
      conn.close()
    }
  }
}
