package me.mig.mars.workers

import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import me.mig.mars.event.MarsCommand.RenewGcmToken
import me.mig.mars.models.JobModel.{DispatchJob, PushJob}
import me.mig.mars.models.NotificationType
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.mysql.FusionDatabase
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 11/22/16.
  */
class JobScheduleWorker @Inject()(configuration: Configuration, implicit val system: ActorSystem, implicit val materializer: Materializer, implicit val db: FusionDatabase, implicit val keyspace: MarsKeyspace, @Named("PushNotificationKafkaProducer") pushNotificationKafkaProducer: ActorRef) extends Actor {

  implicit val executeContext = materializer.executionContext
  implicit val timeout = Timeout(5 seconds)

  override def receive: Receive = {
    case rt: RenewGcmToken => handleRenewToken(rt)
    case job: DispatchJob =>
      Logger.info("Start dispatching scheduled job[Push Notification]...")

      // Need to update the next startTime for service restarting to dispatch the new scheduler.
      keyspace.setNextJob(job.jobId)
        .recover {
          case ex: Throwable =>
            Logger.error("Set the next startTime of job(" + job.jobId + ") encounters error: " + ex.getMessage)
            throw new InterruptedException("Schedule to the next job encounters error: Update the next startTime failed.")
        }

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
          // TODO: Support more types
          NotificationType.withName(job2dispatch.notificationType) match {
            case NotificationType.PUSH =>
              db.getUserTokensByLabelAndCountry(job2dispatch.label, job2dispatch.country).map(
                tokens => {
                  tokens.toList.map(token => PushJob(job.jobId, token._1, job2dispatch.message, token._2, token._3, token._4))
                }
              )
            case NotificationType.ALERT =>
              Logger.info("Coming soon...")
              Future.successful(List.empty[PushJob])
            case x =>
              Logger.info("[" + x + "] not support for scheduling now.")
              Future.successful(List.empty[PushJob])
          }
        }
        .flatMapConcat(pushJobs => Source(pushJobs).map { pushJob =>
            Logger.info("job with tokens: " + pushJob)
            // Publishing to job queue(Kafka) ready for consuming.
            pushNotificationKafkaProducer ! pushJob
          }
        ).runWith(Sink.ignore)
//      keyspace.getJobs(Some(job.jobId))
//        .map( jobList => {
//          if (jobList.isEmpty)
//            throw new NoSuchElementException("Job could not find with ID: " + job.jobId)
//
//          // Suppose there should be only one job by jobId, just get the head of returned list
//          val job2dispatch = jobList.head
//
//          NotificationType.withName(job2dispatch.notificationType) match {
//            case NotificationType.PUSH =>
//              db.getUserTokensByLabelAndCountry(job2dispatch.label, job2dispatch.country).map {
//                tokens => {
//                  Source(tokens.toList)
//                    .map(JobToken.tupled(_).withJobId(job.jobId))
//                    .mapAsync(10) { token =>
//                      Logger.info("caseToken: " + token)
//                      println("caseToken jobId: " + token.jobId)
//                      // Publishing to job queue(Kafka) ready for consuming.
//                      pushNotificationKafkaProducer ? token
//                    }.runWith(Sink.ignore)
//                  Logger.debug("tokens: " + tokens)
//                }
//              }
//            case NotificationType.ALERT =>
//              Logger.info("Coming soon...")
//            case x =>
//              Logger.info("[" + x + "] not support for scheduling now.")
//          }
//        } )
    case x =>
      Logger.warn("Unsupported message to dispatch: " + x)
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