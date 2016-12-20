package me.mig.mars.models

import java.sql.Timestamp
import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.Source
import com.datastax.driver.core.{Cluster, PreparedStatement}
import me.mig.mars.services.JobScheduleService.CreateJob
import play.api.{Configuration, Logger}
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

/**
  * Created by jameshsiao on 12/19/16.
  */
@Singleton
class MarsKeyspace @Inject()(implicit system: ActorSystem, configuration: Configuration, applicationLifecycle: ApplicationLifecycle) {
  import me.mig.mars.models.MarsKeyspace._
  import system.dispatcher

  private val config = configuration.underlying.getConfig("cassandra")
  private final val CREATE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS mars WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '1' }"
  private final val CREATE_JOB_TABLE = "CREATE TABLE IF NOT EXISTS mars.jobs (ID uuid PRIMARY KEY, LABEL int, COUNTRY int, STARTTIME timestamp, INTERVAL double, NOTIFICATIONTYPE ascii, MESSAGE text)"
  private final val INSERT_JOB = "INSERT INTO mars.jobs (ID, label, country, startTime, interval, notificationType, message) VALUES (?, ?, ?, ?, ?, ?, ?)"

  implicit private val materializer = ActorMaterializer()
  implicit private val session = Cluster.builder.addContactPoint(config.getString("hosts")).withPort(config.getInt("port")).build.connect()

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
        bindJob.label: Integer,   // Need specific conversion to Java types
        bindJob.country: Integer, // Need specific conversion to Java types
        bindJob.startTime,
        new java.lang.Double(bindJob.interval), // Need specific conversion to Java types
        bindJob.notificationType,
        bindJob.message
      )
    val sink = CassandraSink[Job](parallelism = 2, preparedStmt, stmtBinder)
    Source.single(
      Job(UUID.randomUUID(), job.label, job.country, new Timestamp(job.startTime), job.interval, job.notificationType, job.message)
    ).runWith(sink).transform[Boolean](
      (Done) => true,
      (ex) => {
        Logger.error("CreateJob error: " + ex.getMessage)
        ex
      }
    )

  }

  applicationLifecycle.addStopHook(() => {
    Future.successful(session.close())
  })
}

object MarsKeyspace {
  case class Job(id: UUID, label: Int, country: Int, startTime: Timestamp, interval: Long, notificationType: String, message: String)
}