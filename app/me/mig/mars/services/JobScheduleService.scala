package me.mig.mars.services

import javax.inject.{Inject, Singleton}

import akka.actor.{ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import me.mig.mars.BaseResponse
import me.mig.mars.models.{FusionDatabase, MarsKeyspace}
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
class JobScheduleService @Inject()(system: ActorSystem, appLifecycle: ApplicationLifecycle, configuration: Configuration, fusionDB: FusionDatabase, keyspace: MarsKeyspace, implicit val materializer: Materializer) {
  import JobScheduleService._
  import system.dispatcher

  // TODO: load stored jobs and dispatching...

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

      // TODO: store the job into cassandra
      keyspace.createJob(job).recover {
        case x: Throwable =>
          Logger.error("Creating job into cassandra encounters error: " + x.getMessage)
          false
      }.map {
        case true =>
          val delay = job.startTime - System.currentTimeMillis()
          val cancellable = system.scheduler.schedule(
            FiniteDuration(delay, MILLISECONDS),
            FiniteDuration(job.interval, MILLISECONDS),
            system.actorOf(Props(new PushNotificationProcessor(configuration.underlying, fusionDB))),
            DispatchJob()
          )
          addLifeCycleStopHook(cancellable)
          CreateJobAck(true)
        case _ =>
          Logger.info("createJob in cassandra failed")
          CreateJobAck(false)
      }
    }

  }

  def getJobs(): Flow[Int, GetJobsAck, _] = {
    Flow[Int].map(id => {
      if (id > 0)
        GetJobsAck(id)
      else
        GetJobsAck(9999)
    })
  }

}

object JobScheduleService {

  case class DispatchJob()

  /** Json requests/responses **/
  // Requests
  case class CreateJob(label: Int, country: Int, startTime: Long, interval: Long, notificationType: String, message: String)
  // Responses
  case class CreateJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse
  case class GetJobsAck(id: Int, override val error: Option[String] = None) extends BaseResponse

  // Json Reads
  implicit val CreateJobReads = Json.reads[CreateJob]
  implicit val CreateJobAckReads = Json.reads[CreateJobAck] // Only for test
  // Json Writes
  implicit val CreateJobWrites = Json.writes[CreateJob] // Only for test
  implicit val CreateJobAckWrites = Json.writes[CreateJobAck]
  implicit val GetJobsAckWrites = Json.writes[GetJobsAck]
}