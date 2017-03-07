package me.mig.mars.repositories.cassandra

import java.lang.{Long => JavaLong, Short => JavaShort}
import java.sql.Timestamp
import java.util.{List => JavaList}
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSink, CassandraSource}
import akka.stream.scaladsl.{Sink, Source}
import com.datastax.driver.core._
import me.mig.mars.models.JobModel.{CreateUpdateJob, Job, JobFullHistory, JobHistory, JobHistoryDetail, JobHistorySuccess, NextJob}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.collection.JavaConverters._
import scala.concurrent.Future

/**
  * Created by jameshsiao on 12/19/16.
  */
@Singleton
class MarsKeyspace @Inject()(configuration: Configuration, applicationLifecycle: ApplicationLifecycle, implicit val system: ActorSystem) {
  import system.dispatcher

  import collection.JavaConversions._
//  import materializer.executionContext

  private val config = configuration.underlying.getConfig("cassandra")
  private final val CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS mars WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '1' }"
  private final val CREATE_JOB_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobs (ID text PRIMARY KEY, CREATOR text, USERS list<text>, LABEL list<smallint>, COUNTRY list<int>, STARTTIME timestamp, ENDTIME timestamp, INTERVAL bigint, NOTIFICATIONTYPE ascii, MESSAGE text, CALLTOACTION map<text, text>, CREATEDTIME timestamp, DISABLED boolean)"
  private final val CREATE_JOB_HISTORY_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobhistory (ID text, CREATOR text, USERS list<text>, LABEL list<smallint>, COUNTRY list<int>, STARTTIME timestamp, ENDTIME timestamp, INTERVAL bigint, NOTIFICATIONTYPE ascii, MESSAGE text, CALLTOACTION map<text, text>, CREATEDTIME timestamp, TOTALCOUNT bigint, PRIMARY KEY (ID, STARTTIME))"
  private final val CREATE_JOB_HISTORY_SUCCESS_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobhistorysuccess (ID text, STARTTIME timestamp, SUCCESSCOUNT counter, FAILURECOUNT counter, PRIMARY KEY (ID, STARTTIME))"
  private final val CREATE_JOB_HISTORY_DETAIL_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobhistorydetail (ID text, STARTTIME timestamp, USER text, SUCCESS boolean, PLATFORM ascii, DEVICETOKEN text, ENDPOINT ascii, REASON text, PRIMARY KEY(ID, STARTTIME, DEVICETOKEN))"
  // Currently might not be used
  private final val CREATE_NOTIFICATION_TYPE_TABLE = "CREATE TABLE IF NOT EXISTS mars.notificationtype ( name ascii, PRIMARY KEY (name) ) WITH caching = {'keys':'NONE','rows_per_partition':'NONE'}"
  private final val CREATE_NOTIFICATION_DESTINATION_TABLE = "CREATE TABLE IF NOT EXISTS mars.notificationdestination ( name ascii, PRIMARY KEY (name) ) WITH caching = {'keys':'NONE','rows_per_partition':'NONE'}"
  private final val CREATE_NOTIFICATION_TEMPLATE_TABLE = "CREATE TABLE IF NOT EXISTS mars.notificationtemplate ( type ascii, destination ascii, language ascii, variety int, mimetype ascii, subject text, body text, createdtime timestamp, updatedtime timestamp, PRIMARY KEY (type, destination, language, variety) ) WITH caching = {'keys':'NONE','rows_per_partition':'NONE'}"

  implicit private val materializer = ActorMaterializer()
  implicit private val session = Cluster.builder.addContactPoints(config.getStringList("hosts").toList: _*).withPort(config.getInt("port")).build.connect()

  init

  private val jobsTable = new JobsTable()
  private val jobHistoryTable = new JobHistoryTable()
  private val jobHistorySuccessTable = new JobHistorySuccessTable()
  private val jobHistoryDetailTable = new JobHistoryDetailTable()
//  private val notificationTypeTable = new NotificationTypeTable()

  // Try to create keyspace and tables if not exist.
  def init() = {
    if (!session.execute(CREATE_KEYSPACE).isExhausted)
      throw new InterruptedException("Creating keyspace in the cassandra encounters error.")

    if (!session.execute(CREATE_JOB_TABLE).isExhausted)
      throw new InterruptedException("Creating job table in the cassandra encounters error.")

    if (!session.execute(CREATE_JOB_HISTORY_TABLE).isExhausted)
      throw new InterruptedException("Creating job history table in the cassandra ecounters error.")

    if (!session.execute(CREATE_JOB_HISTORY_SUCCESS_TABLE).isExhausted)
      throw new InterruptedException("Creating job history success table in the cassandra ecounters error.")

    if (!session.execute(CREATE_JOB_HISTORY_DETAIL_TABLE).isExhausted)
      throw new InterruptedException("Creating job history detail table in the cassandra ecounters error.")

    if (!session.execute(CREATE_NOTIFICATION_TYPE_TABLE).isExhausted)
      throw new InterruptedException("Creating notification type table in the cassandra ecounters error.")
  }

  def createUpdateJob(job: CreateUpdateJob): Future[String] = jobsTable.createUpdateJob(job)
  def getJobs(jobId: Option[String] = None): Future[List[Job]] = jobsTable.getJobs(jobId)
  def setNextJob(jobId: String, delay: Long): Future[String] = jobsTable.setNextJob(jobId, delay)
  def disableJob(jobId: String): Future[Boolean] = jobsTable.disableJob(jobId)
  def createUpdateJobHistory(jobHistory: JobHistory): Future[String] = jobHistoryTable.createUpdateJobHistory(jobHistory)
  def getJobHistory(jobId: String): Future[List[JobFullHistory]] = jobHistoryTable.getJobHistory(jobId)
  def updateJobHistoryUsers(jobId: String, startTime: Timestamp, users: List[String], totalCount: Long): Future[String] =
    jobHistoryTable.updateJobHistoryUsersAndTokenCount(jobId, startTime, users, totalCount)
  def updateJobHistorySuccessCount(jobId: String, startTime: Timestamp): Future[String] =
    jobHistorySuccessTable.updateJobHistorySuccessCount(jobId, startTime)
  def updateJobHistoryFailureCount(jobId: String, startTime: Timestamp): Future[String] =
    jobHistorySuccessTable.updateJobHistoryFailureCount(jobId, startTime)
  def createUpdateJobHistoryDetail(jobHistoryDetail: JobHistoryDetail) = jobHistoryDetailTable.createUpdateJobHistoryDetail(jobHistoryDetail)
//  def getNotificationTypes(): Future[List[String]] = notificationTypeTable.getNotificationTypes()

  applicationLifecycle.addStopHook(() => {
    Future.successful(session.close())
  })

  // Tables
  private[cassandra] class JobsTable() {

    private final val INSERT_JOB = "INSERT INTO mars.jobs (id, creator, users, label, country, startTime, endTime, interval, notificationType, message, callToAction, createdTime, disabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null)"
    private final val SELECT_JOBS = "SELECT * from mars.jobs"
    private final val SELECT_STARTTIME_INTERVAL = "SELECT startTime, interval from mars.jobs where id = "
    private final val SET_NEXT_JOB = "UPDATE mars.jobs SET startTime = ? where id = ?"
    private final val DISABLE_JOB = "UPDATE mars.jobs SET disabled = true where id = ?"
    private val preparedSetNextJobStmt = session.prepare(SET_NEXT_JOB)

    private val rowMapping = (row: Row) =>
      Job(row.getString("id"),
        row.getString("creator"),
        if (row.getList("users", classOf[String]) != null &&
            row.getList("users", classOf[String]).nonEmpty) // Because cassandra will return EMPTY set if value is null.
          Some(row.getList[String]("users", classOf[String]).toList)
        else None,
        if (row.getList("label", classOf[JavaShort]) != null &&
            row.getList("label", classOf[JavaShort]).nonEmpty) // Because cassandra will return EMPTY set if value is null.
          Some(row.getList[JavaShort]("label", classOf[JavaShort]).map(l => l: Short).toList)
        else None,
        if (row.getList("country", classOf[Integer]) != null &&
            row.getList("country", classOf[Integer]).nonEmpty) // Because cassandra will return EMPTY set if value is null.
          Some(row.getList[Integer]("country", classOf[Integer]).map(c => c: Int).toList)
        else None,
        new Timestamp(row.getTimestamp("startTime").getTime),
        if (row.getTimestamp("endTime") != null) Some(new Timestamp(row.getTimestamp("endTime").getTime)) else None,
        Option(row.get[Long]("interval", classOf[Long])),
        row.getString("notificationType"),
        row.getString("message"),
        row.getMap[String, String]("callToAction", classOf[String], classOf[String]).toMap,
        new Timestamp(row.getTimestamp("createdTime").getTime),
        Some(row.getBool("disabled"))
      )

    private[cassandra] def createUpdateJob(job: CreateUpdateJob): Future[String] = {
      val preparedStmt = session.prepare(INSERT_JOB)
      val stmtBinder = (bindJob: Job, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(
          bindJob.id,
          bindJob.creator,
          if (bindJob.users.nonEmpty) bindJob.users.get.asJava else null,  // Need specific conversion to Java types
          if (bindJob.label.nonEmpty) bindJob.label.get.asJava else null,   // Need specific conversion to Java types
          if (bindJob.country.nonEmpty) bindJob.country.get.asJava else null, // Need specific conversion to Java types
          bindJob.startTime,
          bindJob.endTime.orNull,
          if (bindJob.interval.nonEmpty) bindJob.interval.get: JavaLong else null, // Need specific conversion to Java types
          bindJob.notificationType,
          bindJob.message,
          mapAsJavaMap(bindJob.callToAction),
          bindJob.createdTime
        )
      val sink = CassandraSink[Job](parallelism = 2, preparedStmt, stmtBinder)
      val newJob = Job(job.id, job.creator, job.users,
        job.label, job.country, new Timestamp(job.startTime), job.endTime match {
        case Some(x) => Some(new Timestamp(job.endTime.get))
        case None => None
      }, job.interval, job.notificationType, job.message, job.callToAction, new Timestamp(System.currentTimeMillis()))

      Source.single(newJob).runWith(sink).transform[String](
        (Done) => newJob.id,
        (ex) => {
          Logger.error("CreateUpdateJob error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def setNextJob(jobId: String, delay: Long): Future[String] = {
      val stmtBinder = (nextJob: NextJob, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(nextJob.startTime, nextJob.id)
      val sink = CassandraSink[NextJob](parallelism = 1, preparedSetNextJobStmt, stmtBinder)

      CassandraSource(new SimpleStatement(SELECT_STARTTIME_INTERVAL + s"'$jobId'"))
        .map(row => {
          val nextStartTime = new Timestamp(row.getTimestamp(0).getTime + delay)
          Logger.info("To set job(" + jobId + ") with new startTime: " + nextStartTime)
          NextJob(jobId, nextStartTime)
        })
        .runWith(sink).transform(
          (Done) => jobId,
          (ex) => ex
        )
    }

    private[cassandra] def getJobs(jobId: Option[String] = None): Future[List[Job]] = {
      val queryStmt = jobId match {
        case Some(id) =>
          new SimpleStatement(SELECT_JOBS + s" WHERE id='${jobId.orNull}'")
        case None =>
          new SimpleStatement(SELECT_JOBS)
      }
      CassandraSource(queryStmt).runWith(Sink.seq).transform(
        _.map(rowMapping).toList,
        ex => ex
      )
    }

    private[cassandra] def disableJob(jobId: String): Future[Boolean] = {
      val preparedStmt = session.prepare(DISABLE_JOB)
      val stmtBinder = (jobId: String, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(jobId)
      val sink = CassandraSink(parallelism = 1, preparedStmt, stmtBinder)

      Logger.info("Disabling job: " + jobId)
      Source.single(jobId).runWith(sink).transform(
        Done => true,
        ex => {
          Logger.error("Disabling job error: " + ex.getMessage)
          ex
        }
      )
    }

//    private[cassandra] def updateJobs(job: UpdateJob): Future[Done] = {

//    }

  }

  private[cassandra] class JobHistoryTable() {
    private final val UPSERT_JOBHISTORY = "INSERT INTO mars.jobhistory (id, creator, users, label, country, starttime, endtime, interval, notificationtype, message, calltoaction, createdtime, totalcount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private final val UPDATE_JOBHISTORY_USERS_TOTALCOUNT = "UPDATE mars.jobhistory SET users = ?, totalcount = ? WHERE id = ? AND starttime = ?"
    private final val SELECT_JOBHISTORY = "SELECT * from mars.jobhistory WHERE id = ?"

    private final val preparedSelectStmt = session.prepare(SELECT_JOBHISTORY)

    private val rowMapping = (row: Row) =>
      JobHistory(row.getString("id"),
        row.getString("creator"),
        if (row.getList("users", classOf[String]) != null &&
          row.getList("users", classOf[String]).nonEmpty) // Cassandra will return EMPTY set if value is null.
          Some(row.getList[String]("users", classOf[String]).toList)
        else None,
        if (row.getList("label", classOf[JavaShort]) != null &&
          row.getList("label", classOf[JavaShort]).nonEmpty) // Cassandra will return EMPTY set if value is null.
          Some(row.getList[JavaShort]("label", classOf[JavaShort]).map(l => l: Short).toList)
        else None,
        if (row.getList("country", classOf[Integer]) != null &&
          row.getList("country", classOf[Integer]).nonEmpty) // Cassandra will return EMPTY set if value is null.
          Some(row.getList[Integer]("country", classOf[Integer]).map(c => c: Int).toList)
        else None,
        new Timestamp(row.getTimestamp("startTime").getTime),
        if (row.getTimestamp("endTime") != null) Some(new Timestamp(row.getTimestamp("endTime").getTime)) else None,
        Option(row.get[Long]("interval", classOf[Long])),
        row.getString("notificationType"),
        row.getString("message"),
        row.getMap[String, String]("callToAction", classOf[String], classOf[String]).toMap,
        new Timestamp(row.getTimestamp("createdTime").getTime),
        row.get[Long]("totalcount", classOf[Long])
      )

    private[cassandra] def createUpdateJobHistory(jobHistory: JobHistory): Future[String] = {
      val preparedStmt = session.prepare(UPSERT_JOBHISTORY)
      val stmtBinder = (bindJobHistory: JobHistory, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(
          bindJobHistory.id,
          bindJobHistory.creator,
          if (bindJobHistory.users.nonEmpty) bindJobHistory.users.get.asJava else null,  // Need specific conversion to Java types
          if (bindJobHistory.label.nonEmpty) bindJobHistory.label.get.asJava else null,   // Need specific conversion to Java types
          if (bindJobHistory.country.nonEmpty) bindJobHistory.country.get.asJava else null, // Need specific conversion to Java types
          bindJobHistory.startTime,
          bindJobHistory.endTime.orNull,
          if (bindJobHistory.interval.nonEmpty) bindJobHistory.interval.get: JavaLong else null, // Need specific conversion to Java types
          bindJobHistory.notificationType,
          bindJobHistory.message,
          mapAsJavaMap(bindJobHistory.callToAction),
          bindJobHistory.createdTime,
          bindJobHistory.totalCount: JavaLong
        )
      val sink = CassandraSink[JobHistory](parallelism = 2, preparedStmt, stmtBinder)

      Source.single(jobHistory).runWith(sink).transform[String](
        (Done) => jobHistory.id,
        (ex) => {
          Logger.error("Create/update job history error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def updateJobHistoryUsersAndTokenCount(jobId: String, startTime: Timestamp, users: List[String], totalCount: Long): Future[String] = {
      val preparedStmt = session.prepare(UPDATE_JOBHISTORY_USERS_TOTALCOUNT)
      val stmtBinder = (bindJobHistory: (JavaList[String], Long, String, Timestamp), preparedStmt: PreparedStatement) =>
        preparedStmt.bind(bindJobHistory._1, bindJobHistory._2: JavaLong, bindJobHistory._3, bindJobHistory._4)
      val sink = CassandraSink[(JavaList[String], Long, String, Timestamp)](parallelism = 2, preparedStmt, stmtBinder)

      Source.single((users.asJava, totalCount, jobId, startTime)).runWith(sink).transform[String](
        (Done) => jobId,
        (ex) => {
          Logger.error("Update job history users and token count error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def getJobHistory(jobId: String): Future[List[JobFullHistory]] = {
      val queryStmt = new SimpleStatement(SELECT_JOBHISTORY.replace("?", s"'${jobId}'"))
      CassandraSource(queryStmt)
        .map(rowMapping)
        .mapAsync(10) { history =>
          for {
            historySuccess <- jobHistorySuccessTable.getJobHistorySuccess(history.id, history.startTime)
            historyDetail <- jobHistoryDetailTable.getJobHistoryDetail(history.id, history.startTime)
          } yield {
            val (successCount: Long, failureCount: Long) =
              if (historySuccess.nonEmpty)
                (historySuccess.head.successCount, historySuccess.head.failureCount)
              else (0L, 0L)
            JobFullHistory(history.id, history.creator, history.users, history.label, history.country,
              history.startTime, history.endTime, history.interval, history.notificationType, history.message,
              history.callToAction, history.createdTime, history.totalCount, successCount, failureCount,
              historyDetail)
          }

        }
        .runWith(Sink.seq)
        .transform(
          _.toList,
          ex => ex
        )
    }
  }

  private[cassandra] class JobHistorySuccessTable() {
    private final val UPDATE_JOBHISTORY_SUCCESS = "UPDATE mars.jobhistorysuccess SET successcount = successcount + 1 WHERE id = ? AND starttime = ?"
    private final val UPDATE_JOBHISTORY_FAILURE = "UPDATE mars.jobhistorysuccess SET failurecount = failurecount + 1 WHERE id = ? AND starttime = ?"
    private final val SELECT_JOBHISTORY_SUCCESS = "SELECT * from mars.jobhistorysuccess WHERE id = ? AND starttime = ?"

    private val preparedUpdateSuccessStmt = session.prepare(UPDATE_JOBHISTORY_SUCCESS)
    private val preparedUpdateFailureStmt = session.prepare(UPDATE_JOBHISTORY_FAILURE)

    private val rowMapping = (row: Row) =>
      JobHistorySuccess(row.getString("id"),
        new Timestamp(row.getTimestamp("starttime").getTime),
        row.getLong("successcount"),
        row.getLong("failurecount")
      )

    private[cassandra] def updateJobHistorySuccessCount(jobId: String, startTime: Timestamp): Future[String] = {
      val stmtBinder = (bindJob: (String, Timestamp), preparedStmt: PreparedStatement) =>
        preparedStmt.bind(bindJob._1, bindJob._2)
      val sink = CassandraSink[(String, Timestamp)](parallelism = 2, preparedUpdateSuccessStmt, stmtBinder)

      Source.single((jobId, startTime)).runWith(sink).transform[String](
        (Done) => jobId,
        (ex) => {
          Logger.error("Update job history success count error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def updateJobHistoryFailureCount(jobId: String, startTime: Timestamp): Future[String] = {
      val stmtBinder = (bindJob: (String, Timestamp), preparedStmt: PreparedStatement) =>
        preparedStmt.bind(bindJob._1, bindJob._2)
      val sink = CassandraSink[(String, Timestamp)](parallelism = 2, preparedUpdateFailureStmt, stmtBinder)

      Source.single((jobId, startTime)).runWith(sink).transform[String](
        (Done) => jobId,
        (ex) => {
          Logger.error("Update job history success count error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def getJobHistorySuccess(jobId: String, startTime: Timestamp): Future[List[JobHistorySuccess]] = {
      val queryStmt = new SimpleStatement(SELECT_JOBHISTORY_SUCCESS.replaceFirst("\\?", s"'${jobId}'").replace("?", startTime.getTime.toString))
      CassandraSource(queryStmt).runWith(Sink.seq).transform(
        _.map(rowMapping).toList,
        ex => ex
      )
    }
  }

  private[cassandra] class JobHistoryDetailTable() {
    private final val UPSERT_JOBHISTORYDETAIL = "INSERT INTO mars.jobhistorydetail (id, starttime, user, success, platform, devicetoken, endpoint, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    private final val SELECT_JOBHISTORYDETAIL = "SELECT * FROM mars.jobhistorydetail WHERE id = ? AND starttime = ?"
    private final val preparedInsertStmt = session.prepare(UPSERT_JOBHISTORYDETAIL)

    private val rowMapping = (row: Row) =>
      JobHistoryDetail(row.getString("id"),
        new Timestamp(row.getTimestamp("starttime").getTime),
        row.getString("user"),
        row.getBool("success"),
        row.getString("platform"),
        row.getString("devicetoken"),
        Option(row.getString("endpoint")),
        row.getString("reason")
      )

    private[cassandra] def createUpdateJobHistoryDetail(jobHistoryDetail: JobHistoryDetail): Future[String] = {
      val stmtBinder = (bindJob: JobHistoryDetail, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(
          bindJob.id,
          bindJob.startTime,
          bindJob.user,
          bindJob.success: java.lang.Boolean,
          bindJob.platform,
          bindJob.deviceToken,
          if (bindJob.endpoint.nonEmpty) bindJob.endpoint.get else null,
          bindJob.reason
        )
      val sink = CassandraSink[JobHistoryDetail](parallelism = 2, preparedInsertStmt, stmtBinder)

      Source.single(jobHistoryDetail).runWith(sink).transform[String](
        (Done) => jobHistoryDetail.id,
        (ex) => {
          Logger.error("Create/update job history detail error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def getJobHistoryDetail(jobId: String, startTime: Timestamp): Future[List[JobHistoryDetail]] = {
      val queryStmt = new SimpleStatement(SELECT_JOBHISTORYDETAIL.replaceFirst("\\?", s"'${jobId}'").replace("?", startTime.getTime.toString))
      CassandraSource(queryStmt).runWith(Sink.seq).transform(
        _.map(rowMapping).toList,
        ex => ex
      )
    }
  }

//  private[cassandra] class NotificationTypeTable() {
//    private final val SELECT_TYPES = "select * from mars.notificationtype"
//
//    private[cassandra] def getNotificationTypes(): Future[List[String]] = {
//      val queryStmt = new SimpleStatement(SELECT_TYPES)
//
//      CassandraSource(queryStmt).runWith(Sink.seq).transform(
//        _.map(_.getString(0)).toList,
//        ex => ex
//      )
//    }
//  }

}
