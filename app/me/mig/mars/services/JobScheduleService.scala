package me.mig.mars.services

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import me.mig.mars.models.JobModel.{CreateJob, CreateJobAck, DispatchJob, GetJobsAck}
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.mysql.FusionDatabase
import me.mig.mars.workers.push.PushNotificationKafkaConsumer
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.InjectedActorSupport
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 12/13/16.
  */
@Singleton
class JobScheduleService @Inject()(implicit val system: ActorSystem, appLifecycle: ApplicationLifecycle, configuration: Configuration, implicit val fusionDB: FusionDatabase, implicit val keyspace: MarsKeyspace, implicit val materializer: Materializer, @Named("JobScheduleWorker") jobScheduleWorker: ActorRef, pushNotificationKafkaConsumer: PushNotificationKafkaConsumer) extends InjectedActorSupport {
  import system.dispatcher

  // Loading stored jobs and scheduling to dispatch...
  Logger.info("Starting JobScheduleService to load jobs...")
  Source.single("").via(getJobs).map(
    jobsAck => {
      Logger.info("Loading " + jobsAck.data.size + " jobs")
      jobsAck.data.map(
        job => {
          Logger.debug("job loaded: " + job.id)
          // Initializing Kafa consumers
          pushNotificationKafkaConsumer.launch(job.id)
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

  private def scheduleJob(jobId: String, delay: Long, interval: Long): Unit = {
    val cancellable = system.scheduler.schedule(
      FiniteDuration(delay, MILLISECONDS),
      FiniteDuration(interval, MILLISECONDS),
      jobScheduleWorker,
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
      keyspace.createJob(job).transform[CreateJobAck](
        jobId => {
          Logger.info("Job created: " + jobId)
          val delay = job.startTime - System.currentTimeMillis()
          scheduleJob(jobId, delay, job.interval)
          CreateJobAck(true)
        },
        ex => new InterruptedException("Creating job into cassandra encounters error: " + ex.getMessage)
      )
    }

  }

  def getJobs(): Flow[String, GetJobsAck, _] = {
    Flow[String].mapAsync(2)(id => {
      if (id.nonEmpty) {
        keyspace.getJobs(Some(id))
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