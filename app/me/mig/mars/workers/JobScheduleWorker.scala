package me.mig.mars.workers

import java.sql.Timestamp
import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import me.mig.mars.event.MarsCommand.RenewGcmToken
import me.mig.mars.models.JobModel.{DispatchJob, Job, PushJob}
import me.mig.mars.models.NotificationType
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.hive.HiveClient
import me.mig.mars.repositories.mysql.FusionDatabase
import me.mig.mars.services.JobScheduleService
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 11/22/16.
  */
class JobScheduleWorker @Inject()(configuration: Configuration, implicit val system: ActorSystem, implicit val materializer: Materializer, implicit val db: FusionDatabase, implicit val keyspace: MarsKeyspace, @Named("PushNotificationKafkaProducer") pushNotificationKafkaProducer: ActorRef, hiveClient: HiveClient) extends Actor {
  private final val ONE_DAY: Long = 86400000

  implicit val executeContext = materializer.executionContext
  implicit val timeout = Timeout(5 seconds)

  override def receive: Receive = {
    case rt: RenewGcmToken => handleRenewToken(rt)
    case job: DispatchJob =>
      Logger.info("Start dispatching scheduled job[Push Notification]...")

      // Get job info and dispatch the job
      Logger.debug("getJobs: " + job.jobId)
      Source.single(job.jobId)
        .mapAsync(1)(jobId => keyspace.getJobs(Some(job.jobId)))
        .map(jobList => {
          if (jobList.isEmpty)
            throw new NoSuchElementException("Job could not find with ID: " + job.jobId)
          jobList.head
        })
        .mapAsync(2) { job2dispatch =>
          // No interval and endTime set, only schedule once
          if (job2dispatch.interval.isEmpty && job2dispatch.endTime.isEmpty) {
            // TODO: Need to check if job has been canceled and removed successfully
            JobScheduleService.removeRunningJob(job2dispatch.id)
            keyspace.disableJob(job2dispatch.id)
            dispatchJob(job2dispatch)
          } else {
            // Otherwise, check if reach the endTime to stop the job or not.
            // If endTime is not set, means the loop job, assign a maximum value.
            if (job2dispatch.endTime.getOrElse(new Timestamp(Long.MaxValue)).after(job2dispatch.startTime)) {
              Logger.debug("endTime is after startTime")
              scheduleNextJob(job2dispatch)
              dispatchJob(job2dispatch)
            } else {
              // TODO: Need to check if job has been canceled and removed successfully
              JobScheduleService.removeRunningJob(job2dispatch.id)
              keyspace.disableJob(job2dispatch.id)
              throw new IllegalArgumentException("Reached the end time, disabling the job.")
            }
          }
        }
        .map(pushJobs => pushJobs.map { pushJob =>
            Logger.debug("pushJobs: " + pushJobs)
            Logger.info("job with tokens: " + pushJob)
            // Publishing to job queue(Kafka) ready for consuming.
            pushNotificationKafkaProducer ! pushJob
          }
        ).runWith(Sink.ignore)
        .recover {
          case ex => Logger.error("Dispatching job encounters error: " + ex.getMessage)
        }

    case x =>
      Logger.warn("Unsupported message to dispatch: " + x)
  }

  private def scheduleNextJob(job: Job): Future[String] = {
    Logger.debug("scheduleNextJob: " + job)
    // Need to update the next startTime for service restarting to dispatch the new scheduler.
    val interval = if (job.interval.nonEmpty && job.interval.get > 0) job.interval.get else ONE_DAY // Default is one day if not set

    Logger.debug("interval: " + interval)
    val delay: Long =
      if (job.startTime.getTime < System.currentTimeMillis()) {
        Logger.debug("start time is before now")
        ((Math.ceil((System.currentTimeMillis() - job.startTime.getTime) / interval) + 1) * interval).toLong
      }
      else
        interval
    Logger.debug("Start to add RunningJob: " + job.id + " with delay: " + delay)

    Logger.debug("Finite delay: " + FiniteDuration(job.startTime.getTime + delay - System.currentTimeMillis(), MILLISECONDS))
    JobScheduleService.addRunningJob(job.id,
      system.scheduler.scheduleOnce(
        FiniteDuration(job.startTime.getTime + delay - System.currentTimeMillis(), MILLISECONDS),  // The next start time
        self,
        DispatchJob(job.id)
      )
    )
    Logger.debug("Start setNextJob")
    keyspace.setNextJob(job.id, delay)
      .recover {
        case ex: Throwable =>
          Logger.error("Set the next startTime of job(" + job.id + ") encounters error: " + ex.getMessage)
          throw new InterruptedException("Schedule to the next job encounters error: Update the next startTime failed.")
      }
  }

  private def dispatchJob(job: Job): Future[List[PushJob]] = {
    // TODO: Support more types
    Logger.debug("dispatchJob enter")
    NotificationType.withName(job.notificationType) match {
      case NotificationType.PUSH =>
        if (hiveClient.isExist) {
          Logger.debug("Use Hive to query")
          Source(hiveClient.getScheduledJobUsers(job.label, job.country)).mapAsync(2) { user =>
            Logger.debug("user to push: " + user)
            for {
              gcmTokens <- db.getGcmRegToken(user._1)
              iosTokens <- db.getIosDeviceToken(user._1)
            } yield {
              Logger.debug("gcmTokens: " + gcmTokens)
              Logger.debug("iosTokens: " + iosTokens)
              val userTokens = (gcmTokens zip iosTokens).map {
                case (gcmToken, iosToken) =>
                  PushJob(job.id, user._1, job.message, Some(job.callToAction), Some(user._3),
                    if (gcmToken != null) Some(gcmToken.token) else None,
                    if (iosToken != null) Some(iosToken.deviceToken) else None
                  )
              }.toList
              Logger.debug("userTokens(" + user._1 + "): " + userTokens)
              userTokens
            }
          }.runWith(Sink.head).recover {
            case ex: Throwable =>
              Logger.error("Getting user tokens encounters error: " + ex.getMessage)
              List()
          }
//          Future.sequence {
//            val tokens = hiveClient.getScheduledJobUsers(job.label, job.country).map { user =>
//              Logger.debug("user to push: " + user)
//              for {
//                gcmTokens <- db.getGcmRegToken(user._1)
//                iosTokens <- db.getIosDeviceToken(user._1)
//              } yield {
//                Logger.debug("gcmTokens: " + gcmTokens)
//                Logger.debug("iosTokens: " + iosTokens)
//                val userTokens = (gcmTokens zip iosTokens).map {
//                  case (gcmToken, iosToken) =>
//                    PushJob(job.id, user._1, job.message, Some(job.callToAction), Some(user._3),
//                      if (gcmToken != null) Some(gcmToken.token) else None,
//                      if (iosToken != null) Some(iosToken.deviceToken) else None
//                    )
//                }.toList
//                Logger.debug("userTokens(" + user._1 + "): " + userTokens)
//                userTokens
//              }
//            }
//            Logger.debug("tokens: " + tokens)
//            tokens
//          }.map(_.flatten)
        } else {
          Logger.debug("Use mysql to query")
          db.getUserTokensByLabelAndCountry(job.label, job.country).map(
            tokens => {
              tokens.toList.map(token => PushJob(job.id, token._1, job.message, Some(job.callToAction), token._2, token._3, token._4))
            }
          )
        }
      case NotificationType.POPUP =>
        Logger.info("Coming soon...")
        Future.successful(List.empty[PushJob])
      case x =>
        Logger.info("[" + x + "] not support for scheduling now.")
        Future.successful(List.empty[PushJob])
    }
  }

  private def handleRenewToken(renewToken: RenewGcmToken) = {
//    renewToken.tokenType match {
//      case PushNotificationType.GCM =>
        Source.fromFuture[Int](db.updateGcmRegToken(renewToken.userId.asInstanceOf[Int], renewToken.originalToken, renewToken.newToken))
//      case PushNotificationType.APNS =>
//        Source.fromFuture[Int](db.updateIosDeviceToken(renewToken.userId, renewToken.originalToken.getBytes, renewToken.newToken.getBytes))
//      case _ =>
//        Logger.warn("Unsupported token type: " + renewToken)
//    }
  }

}