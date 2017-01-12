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
import me.mig.mars.models.JobModel.{CreateJob, Job, NextJob}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
  * Created by jameshsiao on 12/19/16.
  */
@Singleton
class MarsKeyspace @Inject()(implicit system: ActorSystem, configuration: Configuration, applicationLifecycle: ApplicationLifecycle) {
  import collection.JavaConversions._
  import materializer.executionContext

  private val config = configuration.underlying.getConfig("cassandra")
  private final val CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS mars WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '1' }"
  private final val CREATE_JOB_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobs (ID text PRIMARY KEY, CREATOR text, LABEL list<smallint>, COUNTRY list<int>, STARTTIME timestamp, ENDTIME timestamp, INTERVAL bigint, NOTIFICATIONTYPE ascii, MESSAGE text, CALLTOACTION map<text, text>, CREATEDTIME timestamp, DISABLED boolean)"
  // Currently might not be used
  private final val CREATE_NOTIFICATION_TYPE_TABLE = "CREATE TABLE IF NOT EXISTS mars.notificationtype ( name ascii, PRIMARY KEY (name) ) WITH caching = {'keys':'NONE','rows_per_partition':'NONE'}"
  private final val CREATE_NOTIFICATION_DESTINATION_TABLE = "CREATE TABLE IF NOT EXISTS mars.notificationdestination ( name ascii, PRIMARY KEY (name) ) WITH caching = {'keys':'NONE','rows_per_partition':'NONE'}"
  private final val CREATE_NOTIFICATION_TEMPLATE_TABLE = "CREATE TABLE IF NOT EXISTS mars.notificationtemplate ( type ascii, destination ascii, language ascii, variety int, mimetype ascii, subject text, body text, createdtime timestamp, updatedtime timestamp, PRIMARY KEY (type, destination, language, variety) ) WITH caching = {'keys':'NONE','rows_per_partition':'NONE'}"

  implicit private val materializer = ActorMaterializer()
  implicit private val session = Cluster.builder.addContactPoints(config.getStringList("hosts").toList: _*).withPort(config.getInt("port")).build.connect()

  init

  private val jobsTable = new JobsTable()
//  private val notificationTypeTable = new NotificationTypeTable()

  // Try to create keyspace and tables if not exist.
  def init() = {
    if (!session.execute(CREATE_KEYSPACE).isExhausted)
      throw new InterruptedException("Creating keyspace in the cassandra encounters error.")

    if (!session.execute(CREATE_JOB_TABLE).isExhausted)
      throw new InterruptedException("Creating job table in the cassandra encounters error.")

    if (!session.execute(CREATE_NOTIFICATION_TYPE_TABLE).isExhausted)
      throw new InterruptedException("Creating notification type table in the cassandra ecounters error.")
  }

  def createJob(job: CreateJob): Future[String] = jobsTable.createJob(job)

  def getJobs(jobId: Option[String] = None): Future[List[Job]] = jobsTable.getJobs(jobId)

  def setNextJob(jobId: String, delay: Long): Future[String] = jobsTable.setNextJob(jobId, delay)

  def disableJob(jobId: String): Future[Boolean] = jobsTable.disableJob(jobId)

//  def getNotificationTypes(): Future[List[String]] = notificationTypeTable.getNotificationTypes()

  applicationLifecycle.addStopHook(() => {
    Future.successful(session.close())
  })

  // Tables
  private[cassandra] class JobsTable() {

    private final val INSERT_JOB = "INSERT INTO mars.jobs (id, creator, label, country, startTime, endTime, interval, notificationType, message, callToAction, createdTime) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private final val SELECT_JOBS = "SELECT * from mars.jobs"
    private final val SELECT_STARTTIME_INTERVAL = "SELECT startTime, interval from mars.jobs where id = "
    private final val SET_NEXT_JOB = "UPDATE mars.jobs SET startTime = ? where id = ?"
    private final val DISABLE_JOB = "UPDATE mars.jobs SET disabled = true where id = ?"
    private val preparedSetNextJobStmt = session.prepare(SET_NEXT_JOB)

    private val rowMapping = (row: Row) =>
      Job(row.getString("id"),
        row.getString("creator"),
        row.getList[JavaShort]("label", classOf[JavaShort]).map(l => l: Short).toList,
        row.getList[Integer]("country", classOf[Integer]).map(c => c: Int).toList,
        new Timestamp(row.getTimestamp("startTime").getTime),
        if (row.getTimestamp("endTime") != null) Some(new Timestamp(row.getTimestamp("endTime").getTime)) else None,
        if (row.get[Long]("interval", classOf[Long]) != null) Some(row.get[Long]("interval", classOf[Long])) else None,
        row.getString("notificationType"),
        row.getString("message"),
        row.getMap[String, String]("callToAction", classOf[String], classOf[String]).toMap,
        new Timestamp(row.getTimestamp("createdTime").getTime),
        Some(row.getBool("disabled"))
      )

    private[cassandra] def createJob(job: CreateJob): Future[String] = {
      val preparedStmt = session.prepare(INSERT_JOB)
      val stmtBinder = (bindJob: Job, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(
          bindJob.id,
          bindJob.creator,
          ListBuffer(bindJob.label: _*): JavaList[Short],   // Need specific conversion to Java types
          ListBuffer(bindJob.country: _*): JavaList[Int], // Need specific conversion to Java types
          bindJob.startTime,
          bindJob.endTime.getOrElse(null),
          if (bindJob.interval nonEmpty) bindJob.interval.get: JavaLong else null, // Need specific conversion to Java types
          bindJob.notificationType,
          bindJob.message,
          mapAsJavaMap(bindJob.callToAction),
          bindJob.createdTime
        )
      val sink = CassandraSink[Job](parallelism = 2, preparedStmt, stmtBinder)
      val newJob = Job(job.id, job.creator,
        job.label, job.country, new Timestamp(job.startTime), job.endTime match {
        case Some(x) => Some(new Timestamp(job.endTime.get))
        case None => None
      }, job.interval, job.notificationType, job.message, job.callToAction, new Timestamp(System.currentTimeMillis()))

      Source.single(newJob).runWith(sink).transform[String](
        (Done) => newJob.id,
        (ex) => {
          Logger.error("CreateJob error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def setNextJob(jobId: String, delay: Long): Future[String] = {
      val stmtBinder = (nextJob: NextJob, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(nextJob.startTime, nextJob.id)
      val sink = CassandraSink[NextJob](parallelism = 1, preparedSetNextJobStmt, stmtBinder)

      CassandraSource(new SimpleStatement(SELECT_STARTTIME_INTERVAL + s"'${jobId}'"))
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
          new SimpleStatement(SELECT_JOBS + s" WHERE id='${jobId.getOrElse(null)}'")
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
