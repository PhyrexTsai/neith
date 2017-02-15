package me.mig.mars.services

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.cluster.{Cluster, MemberStatus}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import me.mig.mars.models.JobModel.{CreateJob, CreateJobAck, DispatchJob, GetJobsAck, ScheduleJob}
import me.mig.mars.models.NotificationModel.GetNotificationTypesAck
import me.mig.mars.models.NotificationType
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.mysql.FusionDatabase
import me.mig.mars.services.JobScheduleService.JobScheduler
import me.mig.mars.workers.push.PushNotificationKafkaConsumer
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.InjectedActorSupport
import play.api.{Configuration, Logger}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 12/13/16.
  */
@Singleton
class JobScheduleService @Inject()(implicit val system: ActorSystem, appLifecycle: ApplicationLifecycle, configuration: Configuration, implicit val fusionDB: FusionDatabase, implicit val keyspace: MarsKeyspace, implicit val materializer: Materializer, @Named("JobScheduleWorker") jobScheduleWorker: ActorRef, pushNotificationKafkaConsumer: PushNotificationKafkaConsumer) extends InjectedActorSupport {
  import system.dispatcher

  // Initializing singleton actor to perform the job scheduling in the cluster.
  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(classOf[JobScheduler], jobScheduleWorker),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    name = "JobScheduler"
  )
  Logger.debug("Number of nodes in cluster: " + Cluster(system).state.members.filter(_.status == MemberStatus.Up))  

  // Loading stored jobs and scheduling to dispatch...
  Logger.info("Starting JobScheduleService to load jobs...")
  Source.single("").via(getJobs()).map(
    jobsAck => {
      Logger.info("Loading " + jobsAck.data.size + " jobs")
      jobsAck.data.foreach(
        job => {
          if (!job.disabled.getOrElse(false)) {
            Logger.debug("job loaded: " + job.id)
            // Initializing Kafa consumers
            // Replace all spaces into underscore because Kafka seems not allow space in topic name.
            pushNotificationKafkaConsumer.launch(job.id.replaceAll(" ", "_"))
            val delay =
              // If start time is before now, run the job immediately.
              if (job.startTime.getTime < System.currentTimeMillis())
                0
              else
                job.startTime.getTime - System.currentTimeMillis()

            scheduleJob(job.id, delay)
          }
        }
      )
    }
  ).to(Sink.ignore).run()

  addLifeCycleStopHook()

  private def scheduleJob(jobId: String, delay: Long): Unit = {
//    Logger.debug("scheduleJob delay: " + delay)
//    if (JobScheduleService.isExist(jobId)) {
//      Logger.warn(s"Job ${jobId} already running, stop and start the new one.")
//      JobScheduleService.removeRunningJob(jobId)
//    }
//    val cancellable = system.scheduler.scheduleOnce(
//      FiniteDuration(delay, MILLISECONDS),
//      jobScheduleWorker,
//      DispatchJob(jobId)
//    )
////    addLifeCycleStopHook(cancellable)
//    JobScheduleService.addRunningJob(jobId, cancellable)
  }

  @deprecated(message = "Since jobs will change frequently, we do not need to bind each job on the stop hook.", since = "Next release if service is running stabl")
  private def addLifeCycleStopHook(job: Cancellable): Unit = {
    // Application Hooks
    appLifecycle.addStopHook { () =>
      Future.successful(job.cancel())
    }
  }

  private def addLifeCycleStopHook(): Unit = {
    appLifecycle.addStopHook { () =>
      Future.successful( JobScheduleService.cancelAllJobs() )
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
          scheduleJob(jobId, delay)
          // Initializing Kafa consumers
          // Replace all spaces into underscore because Kafka seems not allow space in topic name.
          pushNotificationKafkaConsumer.launch(job.id.replaceAll(" ", "_"))
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

  def getNotificationTypes(): Flow[Int, GetNotificationTypesAck, _] = {
    Flow[Int].map(_ =>
      GetNotificationTypesAck(NotificationType.values.map(_.toString).toList)
//      keyspace.getNotificationTypes().transform(
//        GetNotificationTypesAck(_),
//        ex => ex
//      )
    )
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
    canceled
  }

  def cancelAllJobs(): Unit =
    runningJobMap.map { jobTuple =>
      jobTuple._2.cancel()
      runningJobMap -= jobTuple._1
    }

  class JobScheduler(jobScheduleWorker: ActorRef) extends Actor {
    override def receive: Receive = {
      case ScheduleJob(jobId, delay) => scheduleJob(jobId, delay)
    }

    private def scheduleJob(jobId: String, delay: Long): Unit = {
      Logger.debug("scheduleJob delay: " + delay)
      if (JobScheduleService.isExist(jobId)) {
        Logger.warn(s"Job ${jobId} already running, stop and start the new one.")
        removeRunningJob(jobId)
      }
      val cancellable = context.system.scheduler.scheduleOnce(
        FiniteDuration(delay, MILLISECONDS),
        jobScheduleWorker,
        DispatchJob(jobId)
      )
      //    addLifeCycleStopHook(cancellable)
      addRunningJob(jobId, cancellable)
    }
  }
}