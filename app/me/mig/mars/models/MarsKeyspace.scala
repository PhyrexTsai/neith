package me.mig.mars.models

import java.lang.{Long => JavaLong}
import java.sql.Timestamp
import java.util.{UUID, List => JavaList}
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSink, CassandraSource}
import akka.stream.scaladsl.{Sink, Source}
import com.datastax.driver.core.{Cluster, PreparedStatement, SimpleStatement}
import me.mig.mars.services.JobScheduleService.CreateJob
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
  * Created by jameshsiao on 12/19/16.
  */
@Singleton
class MarsKeyspace @Inject()(implicit system: ActorSystem, configuration: Configuration, applicationLifecycle: ApplicationLifecycle) {
  import me.mig.mars.models.MarsKeyspace._
  import system.dispatcher

  import collection.JavaConversions._

  private val config = configuration.underlying.getConfig("cassandra")
  private final val CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS mars WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '1' }"
  private final val CREATE_JOB_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobs (ID uuid PRIMARY KEY, LABEL list<int>, COUNTRY list<int>, STARTTIME timestamp, ENDTIME timestamp, INTERVAL bigint, NOTIFICATIONTYPE ascii, MESSAGE text, CALLTOACTION map<text, text>)"
  private final val INSERT_JOB = "INSERT INTO mars.jobs (id, label, country, startTime, endTime, interval, notificationType, message, callToAction) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
  private final val SELECT_JOBS = "SELECT * from mars.jobs"

  implicit private val materializer = ActorMaterializer()
  implicit private val session = Cluster.builder.addContactPoints(config.getStringList("hosts").toList: _*).withPort(config.getInt("port")).build.connect()

  init

  def init() = {
    if (!session.execute(CREATE_KEYSPACE).isExhausted)
      throw new InterruptedException("Creating keyspace in the cassandra encounters error.")

    if (!session.execute(CREATE_JOB_TABLE).isExhausted)
      throw new InterruptedException("Creating job table in the cassandra encounters error.")
  }

  def createJob(job: CreateJob): Future[Boolean] = {
    val preparedStmt = session.prepare(INSERT_JOB)
    val stmtBinder = (bindJob: Job, preparedStmt: PreparedStatement) =>
      preparedStmt.bind(
        bindJob.id,
        ListBuffer(bindJob.label: _*): JavaList[Int],   // Need specific conversion to Java types
        ListBuffer(bindJob.country: _*): JavaList[Int], // Need specific conversion to Java types
        bindJob.startTime,
        bindJob.endTime.getOrElse(null),
        bindJob.interval: JavaLong, // Need specific conversion to Java types
        bindJob.notificationType,
        bindJob.message,
        mapAsJavaMap(bindJob.callToAction)
      )
    val sink = CassandraSink[Job](parallelism = 2, preparedStmt, stmtBinder)
    Source.single(
      Job(UUID.randomUUID(), job.label, job.country, new Timestamp(job.startTime), job.endTime match {
        case Some(x) => Some(new Timestamp(job.endTime.get))
        case None => None
      }, job.interval, job.notificationType, job.message, job.callToAction)
    ).runWith(sink).transform[Boolean](
      (Done) => true,
      (ex) => {
        Logger.error("CreateJob error: " + ex.getMessage)
        ex
      }
    )
  }

  def getJobs(): Future[List[Job]] = {
    val queryStmt = new SimpleStatement(SELECT_JOBS)
    val rows = CassandraSource(queryStmt).runWith(Sink.seq)
    rows.transform(
      _.map(row =>
        Job(row.getUUID("id"),
            row.getList[Integer]("label", classOf[Integer]).map(l => l: Int).toList,
            row.getList[Integer]("country", classOf[Integer]).map(c => c: Int).toList,
            new Timestamp(row.getTimestamp("startTime").getTime),
            if (row.getTimestamp("endTime") != null) Some(new Timestamp(row.getTimestamp("endTime").getTime)) else None,
            row.get[Long]("interval", classOf[Long]),
            row.getString("notificationType"),
            row.getString("message"),
            row.getMap[String, String]("callToAction", classOf[String], classOf[String]).toMap)
      ).toList,
      ex => ex
    )
  }

  applicationLifecycle.addStopHook(() => {
    Future.successful(session.close())
  })

}

object MarsKeyspace {
  case class Job(id: UUID, label: List[Int], country: List[Int], startTime: Timestamp, endTime: Option[Timestamp], interval: Long, notificationType: String, message: String, callToAction: Map[String, String])
}