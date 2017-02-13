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
  private final val SELECT_SCHEDULED_JOB_USERS = "select a.id userid, b.type, a.username, a.countryid from dm.dim_user a join ds.fct_user_label b on a.countryid in (?) and b.type in (?) and a.id = b.userid"
  private val config = configuration.underlying.getConfig("hive")

  val isExist: Boolean = config.getString("jdbcUrl") != null && config.getString("jdbcUrl") != ""

  def getScheduledJobUsers(labels: List[Short], countries: List[Int]) = {
    Class.forName(config.getString("driver"))
    val conn = DriverManager.getConnection(config.getString("jdbcUrl"), config.getString("user"), config.getString("password"))
    val preparedSql = SELECT_SCHEDULED_JOB_USERS.replaceFirst("\\?", countries.mkString(",")).replace("?", labels.mkString(","))
    Logger.debug("preparedSql: " + preparedSql)
    val stmt: PreparedStatement = conn.prepareStatement(preparedSql)
    try {
//      val countryArray: java.sql.Array = conn.createArrayOf("INTEGER", countries.asJava.toArray)
//      for (ct <- countries.asJava.toArray) {
//        Logger.debug("countries to sql array[]: " + ct)
//      }
//      val labelArray: java.sql.Array = conn.createArrayOf("TINYINT", labels.asJava.toArray)
//      for (lb <- labels.asJava.toArray) {
//        Logger.debug("labels to sql array[]: " + lb)
//      }
//      Logger.debug("countryArray: " + countryArray)
//      stmt.setArray(1, countryArray)
//      stmt.setArray(2, labelArray)
      stmt.setFetchSize(MAX_FETCH_SIZE)
      val res = stmt.executeQuery()
      var resultList = mutable.ListBuffer[(Int, String, String, Int)]()
      while (res.next()) {
        Logger.debug( "res: " + (res.getInt(1), res.getString(2), res.getString(3), res.getInt(4)) )
        resultList :+ (res.getInt(1), res.getString(2), res.getString(3), res.getInt(4))
      }
      for (res <- resultList) {
        Logger.debug("item of resultList: " + res)
      }
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
