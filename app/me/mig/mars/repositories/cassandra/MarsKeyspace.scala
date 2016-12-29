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

  private val config = configuration.underlying.getConfig("cassandra")
  private final val CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS mars WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '1' }"
  private final val CREATE_JOB_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobs (ID text PRIMARY KEY, LABEL list<smallint>, COUNTRY list<int>, STARTTIME timestamp, ENDTIME timestamp, INTERVAL bigint, NOTIFICATIONTYPE ascii, MESSAGE text, CALLTOACTION map<text, text>)"

  implicit private val materializer = ActorMaterializer()
  implicit private val session = Cluster.builder.addContactPoints(config.getStringList("hosts").toList: _*).withPort(config.getInt("port")).build.connect()

  init

  private val jobsTable = new JobsTable()

  // Try to create keyspace and tables if not exist.
  def init() = {
    if (!session.execute(CREATE_KEYSPACE).isExhausted)
      throw new InterruptedException("Creating keyspace in the cassandra encounters error.")

    if (!session.execute(CREATE_JOB_TABLE).isExhausted)
      throw new InterruptedException("Creating job table in the cassandra encounters error.")
  }

  def createJob(job: CreateJob): Future[String] = jobsTable.createJob(job)

  def getJobs(jobId: Option[String] = None): Future[List[Job]] = jobsTable.getJobs(jobId)

  def setNextJob(jobId: String): Future[String] = jobsTable.setNextJob(jobId)

  applicationLifecycle.addStopHook(() => {
    Future.successful(session.close())
  })

  // Tables
  private[cassandra] class JobsTable() {
    import materializer.executionContext

    private final val INSERT_JOB = "INSERT INTO mars.jobs (id, label, country, startTime, endTime, interval, notificationType, message, callToAction) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private final val SELECT_JOBS = "SELECT * from mars.jobs"
    private final val SELECT_STARTTIME_INTERVAL = "SELECT startTime, interval from mars.jobs where id = "
    private final val SET_NEXT_JOB = "UPDATE mars.jobs SET startTime = ? where id = ?"
    private val preparedSetNextJobStmt = session.prepare(SET_NEXT_JOB)

    private val rowMapping = (row: Row) =>
      Job(row.getString("id"),
        row.getList[JavaShort]("label", classOf[JavaShort]).map(l => l: Short).toList,
        row.getList[Integer]("country", classOf[Integer]).map(c => c: Int).toList,
        new Timestamp(row.getTimestamp("startTime").getTime),
        if (row.getTimestamp("endTime") != null) Some(new Timestamp(row.getTimestamp("endTime").getTime)) else None,
        row.get[Long]("interval", classOf[Long]),
        row.getString("notificationType"),
        row.getString("message"),
        row.getMap[String, String]("callToAction", classOf[String], classOf[String]).toMap)

    private[cassandra] def createJob(job: CreateJob): Future[String] = {
      val preparedStmt = session.prepare(INSERT_JOB)
      val stmtBinder = (bindJob: Job, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(
          bindJob.id,
          ListBuffer(bindJob.label: _*): JavaList[Short],   // Need specific conversion to Java types
          ListBuffer(bindJob.country: _*): JavaList[Int], // Need specific conversion to Java types
          bindJob.startTime,
          bindJob.endTime.getOrElse(null),
          bindJob.interval: JavaLong, // Need specific conversion to Java types
          bindJob.notificationType,
          bindJob.message,
          mapAsJavaMap(bindJob.callToAction)
        )
      val sink = CassandraSink[Job](parallelism = 2, preparedStmt, stmtBinder)
      val newJob = Job(job.id, job.label, job.country, new Timestamp(job.startTime), job.endTime match {
        case Some(x) => Some(new Timestamp(job.endTime.get))
        case None => None
      }, job.interval, job.notificationType, job.message, job.callToAction)

      Source.single(newJob).runWith(sink).transform[String](
        (Done) => newJob.id,
        (ex) => {
          Logger.error("CreateJob error: " + ex.getMessage)
          ex
        }
      )
    }

    private[cassandra] def setNextJob(jobId: String): Future[String] = {
      val stmtBinder = (nextJob: NextJob, preparedStmt: PreparedStatement) =>
        preparedStmt.bind(nextJob.startTime, nextJob.id)
      val sink = CassandraSink[NextJob](parallelism = 1, preparedSetNextJobStmt, stmtBinder)

      CassandraSource(new SimpleStatement(SELECT_STARTTIME_INTERVAL + s"'${jobId}'"))
        .map(row => {
          val nextStartTime = new Timestamp(row.getTimestamp(0).getTime + row.getLong(1))
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

//    private[cassandra] def updateJobs(job: UpdateJob): Future[Done] = {

//    }

  }
}
