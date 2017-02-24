package me.mig.mars.services

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.cluster.{Cluster, MemberStatus}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import me.mig.mars.models.JobModel.{CreateUpdateJob, CreateUpdateJobAck, DeleteJobAck, DispatchJob, GetJobHistoryAck, GetJobsAck, Job, ScheduleJob}
import me.mig.mars.models.NotificationModel.GetNotificationTypesAck
import me.mig.mars.models.NotificationType
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.mysql.FusionDatabase
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 12/13/16.
  */
@Singleton
class JobScheduleService @Inject()(appLifecycle: ApplicationLifecycle, configuration: Configuration, @Named("JobScheduleWorker") jobScheduleWorker: ActorRef, implicit val system: ActorSystem, implicit val materializer: Materializer, fusionDB: FusionDatabase, keyspace: MarsKeyspace) {
  import system.dispatcher

  implicit val timeout: Timeout = 5 seconds

  // Initializing singleton actor to perform the job scheduling in the cluster.
  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = propsJobScheduler(),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    name = "JobScheduler"
  )
  Logger.debug("Number of nodes in cluster: " + Cluster(system).state.members.filter(_.status == MemberStatus.Up))

  // Proxy of cluster singleton actor
  val jobSchedulerProxy = system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/JobScheduler",
    settings = ClusterSingletonProxySettings(system)),
    name = "JobSchedulerProxy")

  addLifeCycleStopHook()

  private def addLifeCycleStopHook(): Unit = {
    appLifecycle.addStopHook { () =>
      Future.successful( JobScheduleService.cancelAllJobs() )
    }
  }

  def createUpdateJob(): Flow[CreateUpdateJob, CreateUpdateJobAck, _] = {

    Flow[CreateUpdateJob].mapAsync(2) { job =>
      Logger.info("Starting createUpdateJob...")

      if (job.users.isEmpty && job.label.isEmpty && job.country.isEmpty)
        throw new IllegalArgumentException("No user, label or country specified, please provide at least one criteria for query.")

      if (job.startTime < System.currentTimeMillis())
        throw new IllegalArgumentException("StartTime is before now.")

      // Store the job into cassandra
      keyspace.createUpdateJob(job).transform[ScheduleJob](
        jobId => {
          Logger.info("Job created: " + jobId)
//          val delay = job.startTime - System.currentTimeMillis()
          //          scheduleJob(jobId, delay)
          ScheduleJob(jobId)
        },
        ex => new InterruptedException("Creating/updating job into cassandra encounters error: " + ex.getMessage)
      )
    }.mapAsync[String](1) { scheduleJob =>
      (jobSchedulerProxy ? scheduleJob).map(x => scheduleJob.jobId)
    }.map { jobId =>
      CreateUpdateJobAck(true)
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

  def deleteJob(): Flow[String, DeleteJobAck, _] = {
    Flow[String].mapAsync(2)(id => {
      keyspace.disableJob(id)
        .transform(
          success => {
            // Remove job from running map
            JobScheduleService.removeRunningJob(id)
            DeleteJobAck(success)
          },
          ex => ex
        )
    })
  }

  def getJobHistory(): Flow[String, GetJobHistoryAck, _] = {
    Flow[String].mapAsync(2)(
      keyspace.getJobHistory(_).transform(
        result => GetJobHistoryAck(result),
        ex => ex
      )
    )
  }

  def getNotificationTypes(): Flow[Int, GetNotificationTypesAck, _] = {
    Flow[Int].map(_ =>
      GetNotificationTypesAck(NotificationType.values.map(_.toString).toList)
    )
  }

  def propsJobScheduler() = Props(new JobScheduler())

  // Real actor to run the scheduling jobs...
  class JobScheduler() extends Actor {
    // Loading stored jobs and scheduling to dispatch...
    Logger.info("Starting JobScheduler to load jobs...")
    fetchJobs("")

    override def receive: Receive = {
      case ScheduleJob(jobId) =>
        fetchJobs(jobId)
        sender ! jobId
    }

    private def fetchJobs(jobId: String): Unit = {
      Source.single(jobId)
        .via(getJobs())
        .map(
          jobsAck => {
            Logger.info("Loading " + jobsAck.data.size + " jobs")
            jobsAck.data
          }
        )
        .mapConcat[Job](_.toList)
        .map{ job =>
          if (!job.disabled.getOrElse(false)) {
            Logger.debug("job loaded: " + job.id)
            scheduleJob(job)
          }
        }
        .runWith(Sink.head)
    }

    private def scheduleJob(job: Job): Unit = {
      val delay =
        // If start time is before now, run the job immediately.
        if (job.startTime.getTime < System.currentTimeMillis())
          0
        else
          job.startTime.getTime - System.currentTimeMillis()
      Logger.debug("scheduleJob delay: " + delay)
      if (JobScheduleService.isExist(job.id)) {
        Logger.warn(s"Job ${job.id} already running, stop and start the new one.")
        JobScheduleService.removeRunningJob(job.id)
      }
      val cancellable = context.system.scheduler.scheduleOnce(
        FiniteDuration(delay, MILLISECONDS),
        jobScheduleWorker,
        DispatchJob(job.id)
      )
      JobScheduleService.addRunningJob(job.id, cancellable)
    }
  }

}

object JobScheduleService {
  private val runningJobMap = mutable.HashMap[String, Cancellable]()

  def isExist(jobId: String): Boolean = {
    runningJobMap.get(jobId) nonEmpty
  }

  def addRunningJob(jobId: String, scheduled: Cancellable): Unit = {
    runningJobMap += (jobId -> scheduled)
    Logger.debug("runningJobMap: " + runningJobMap)
  }

  def removeRunningJob(jobId: String): Boolean = {
    val canceled = runningJobMap.get(jobId).get.cancel()
    runningJobMap -= jobId
    Logger.debug("runningJobMap: " + runningJobMap)
    canceled
  }

  def cancelAllJobs(): Unit =
    runningJobMap.map { jobTuple =>
      jobTuple._2.cancel()
      runningJobMap -= jobTuple._1
    }
}