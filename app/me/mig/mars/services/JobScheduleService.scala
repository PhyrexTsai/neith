package me.mig.mars.services

import javax.inject.{Inject, Singleton}

import akka.actor.{ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import me.mig.mars.BaseResponse
import me.mig.mars.models.FusionDatabase
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
class JobScheduleService @Inject()(val system: ActorSystem, appLifecycle: ApplicationLifecycle, configuration: Configuration, fusionDB: FusionDatabase, implicit val materializer: Materializer) {
  import JobScheduleService._
  import system.dispatcher

  private def addLifeCycleStopHook(job: Cancellable): Unit = {
    // Application Hooks
    appLifecycle.addStopHook { () =>
      Future.successful(job.cancel())
    }
  }

  def createJob(): Flow[CreateJob, CreateJobAck, _] = {
    Flow[CreateJob].map( job => {
      Logger.info("Starting createJob...")
      if (job.startTime < System.currentTimeMillis()) throw new IllegalArgumentException("StartTime is before now.")
      val delay = job.startTime - System.currentTimeMillis()
      Logger.info("delay: " + delay)
      Logger.info("interval: " + job.interval)
      Logger.info("system: " + system.startTime)
      val cancellable = system.scheduler.schedule(
        FiniteDuration(delay, MILLISECONDS),
        FiniteDuration(job.interval, MILLISECONDS),
        system.actorOf( Props(new PushNotificationProcessor(configuration.underlying, fusionDB)) ),
        DispatchJob()
      )
      addLifeCycleStopHook(cancellable)
      // TODO: store the job into cassandra
      CreateJobAck(true)
    } )
  }
}

object JobScheduleService {

  case class DispatchJob()

  /** Json requests/responses **/
  // Requests
  case class CreateJob(label: Int, country: Int, startTime: Long, interval: Long, notificationType: Int)
  case class GetJobs()
  // Responses
  case class CreateJobAck(success: Boolean, override val error: Option[String] = None) extends BaseResponse
  case class GetJobsAck(jobs: List[Job])

  // Json Reads
  implicit val CreateJobReads = Json.reads[CreateJob]
  implicit val CreateJobAckReads = Json.reads[CreateJobAck]
  // Json Writes
  implicit val CreateJobWrites = Json.writes[CreateJob] // Only for test
  implicit val CreateJobAckWrites = Json.writes[CreateJobAck]
}