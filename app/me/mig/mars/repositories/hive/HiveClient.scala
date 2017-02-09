package me.mig.mars.repositories.hive

import java.sql.{Connection, DriverManager, PreparedStatement}
import javax.inject.Inject

import play.api.Configuration

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
    val countryArray: java.sql.Array = conn.createArrayOf("INTEGER", countries.asJava.toArray)
    val labelArray: java.sql.Array = conn.createArrayOf("SMALLINT", labels.asJava.toArray)
    stmt.setArray(0, countryArray)
    stmt.setArray(1, labelArray)
    val res = stmt.executeQuery()
    val resultList = mutable.ListBuffer[(Int, String, String, Int)]()
    while (res.next()) {
      resultList :+ (res.getInt(0), res.getString(1), res.getString(2), res.getInt(3))
    }
    resultList.toList
  }
}
