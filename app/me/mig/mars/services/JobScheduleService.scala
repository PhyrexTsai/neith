package me.mig.mars.services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import me.mig.mars.BaseResponse
import me.mig.mars.models.JobModel.Job
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.mysql.FusionDatabase
import me.mig.mars.workers.PushNotificationProcessor
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 12/13/16.
  */
@Singleton
class JobScheduleService @Inject()(system: ActorSystem, appLifecycle: ApplicationLifecycle, configuration: Configuration, implicit val fusionDB: FusionDatabase, implicit val keyspace: MarsKeyspace, implicit val materializer: Materializer) {
  import JobScheduleService._
  import system.dispatcher

  // TODO: loading stored jobs and dispatching...
  Logger.info("Starting JobScheduleService to load jobs...")
  Source.single("").via(getJobs).map(
    jobsAck => {
      Logger.info("Loading " + jobsAck.data.size + " jobs")
      jobsAck.data.map(
        job => {
          Logger.debug("job loaded: " + job)
          val now = System.currentTimeMillis()
          var delay = job.startTime.getTime - now
          if (delay < 0) {
            Logger.info("Start time is before current time, add interval to restart")
            delay += job.interval
            keyspace.setNextJob(job.id)
              .recover{ case ex: Throwable => Logger.error(ex.getMessage) }
          }

          scheduleJob(job.id, delay, job.interval)
        }
      )
    }
  ).to(Sink.ignore).run()

  private def scheduleJob(jobId: UUID, delay: Long, interval: Long): Unit = {
    val cancellable = system.scheduler.schedule(
      FiniteDuration(delay, MILLISECONDS),
      FiniteDuration(interval, MILLISECONDS),
      system.actorOf(PushNotificationProcessor.props(configuration.underlying)),
      DispatchJob(jobId)
    )
    addLifeCycleStopHook(cancellable)
  }

  private def addLifeCycleStopHook(job: Cancellable): Unit = {
    // Application Hooks
    appLifecycle.addStopHook { () =>
      Future.successful(job.cancel())
    }
  }

  def createJob(): Flow[CreateJob, CreateJobAck, _] = {

    Flow[CreateJob].mapAsync(2) { job =>
      Logger.info("Starting createJob...")

      if (job.startTime < System.currentTimeMillis())
        throw new IllegalArgumentException("StartTime is before now.")

      // Store the job into cassandra
      keyspace.createJob(job).recover {
        case x: Throwable =>
          Logger.error("Creating job into cassandra encounters error: " + x.getMessage)
          null
      }.map {
        case jobId: UUID =>
          Logger.info("Job created: " + jobId)
          val delay = job.startTime - System.currentTimeMillis()
          scheduleJob(jobId, delay, job.interval)
          CreateJobAck(true)
        case _ =>
          Logger.info("createJob in cassandra failed")
          CreateJobAck(false)
      }
    }

  }

  def getJobs(): Flow[String, GetJobsAck, _] = {
    Flow[String].mapAsync(2)(id => {
      if (id.nonEmpty) {
        keyspace.getJobs(Some(UUID.fromString(id)))
          .transform(
            jobs => GetJobsAck(jobs),
            ex => ex
          )
      }
      else {
        keyspace.getJobs().transform(
          jobs => GetJobsAck(jobs),
          ex => ex
        )
      }
    })
  }

}

object JobScheduleService {

  case class DispatchJob(jobId: UUID)

  /** Json requests/responses **/
  // Requests
  case class CreateJob(label: List[Short], country: List[Int], startTime: Long, endTime: Option[Long] = None, interval: Long = 60000, notificationType: String, message: String, callToAction: Map[String, String])
  // Responses
  case class CreateJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse
  case class GetJobsAck(data: List[Job], override val error: Option[String] = None) extends BaseResponse

  // Json Reads
  implicit val CreateJobReads = Json.reads[CreateJob]
  implicit val CreateJobAckReads = Json.reads[CreateJobAck] // Only for test
  // Json Writes
  implicit val CreateJobWrites = Json.writes[CreateJob] // Only for test
  implicit val CreateJobAckWrites = Json.writes[CreateJobAck]
  implicit val JobsWrites = Json.writes[Job]
  implicit val GetJobsAckWrites = Json.writes[GetJobsAck]
}