package me.mig.mars.repositories.hive

import java.sql._
import javax.inject.Inject

import play.api.{Configuration, Logger}

import scala.collection.mutable
import scala.collection.JavaConverters._

/**
  * Created by jameshsiao on 2/8/17.
  */
class HiveClient @Inject()(configuration: Configuration) {
  private final val SELECT_SCHEDULED_JOB_USERS = "select a.id userid, b.type, a.username, a.countryid from dm.dim_user a join ds.fct_user_label b on a.countryid in ? and b.type in ? and a.id = b.userid"
  private val config = configuration.underlying.getConfig("hive")
  private var conn: Connection = null

  var isExist: Boolean = false

  if (config.getString("jdbcUrl") != null && config.getString("jdbcUrl") != "") {
    Class.forName(config.getString("driver"))
    conn = DriverManager.getConnection(config.getString("jdbcUrl"), config.getString("user"), config.getString("password"))
    isExist = true
  }

  def getScheduledJobUsers(labels: List[Short], countries: List[Int]) = {
    val stmt: PreparedStatement = conn.prepareStatement(SELECT_SCHEDULED_JOB_USERS)
    Logger.debug("stmt: " + stmt)
    try {
      val countryArray: java.sql.Array = conn.createArrayOf("INTEGER", countries.asJava.toArray)
      for (ct <- countries.asJava.toArray) {
        Logger.debug("countries to sql array[]: " + ct)
      }
      val labelArray: java.sql.Array = conn.createArrayOf("TINYINT", labels.asJava.toArray)
      for (lb <- labels.asJava.toArray) {
        Logger.debug("labels to sql array[]: " + lb)
      }
      Logger.debug("countryArray: " + countryArray)
      stmt.setArray(1, countryArray)
      stmt.setArray(2, labelArray)
      val res = stmt.executeQuery()
      val resultList = mutable.ListBuffer[(Int, String, String, Int)]()
      while (res.next()) {
        resultList :+ (res.getInt(0), res.getString(1), res.getString(2), res.getInt(3))
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
    }
  }
}
