package me.mig.mars.workers

import akka.actor.{Actor, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import me.mig.mars.event.MarsCommand.{PushNotificationType, RenewToken}
import me.mig.mars.models.JobModel.JobToken
import me.mig.mars.models.NotificationType
import me.mig.mars.repositories.cassandra.MarsKeyspace
import me.mig.mars.repositories.mysql.FusionDatabase
import me.mig.mars.services.JobScheduleService.DispatchJob
import play.api.Logger

/**
  * Created by jameshsiao on 11/22/16.
  */
class PushNotificationProcessor(config: Config)(implicit val materializer: Materializer, implicit val db: FusionDatabase, implicit val keyspace: MarsKeyspace) extends Actor {

  implicit val executeContext = materializer.executionContext

  override def receive: Receive = {
    case rt: RenewToken => handleRenewToken(rt)
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
      keyspace.getJobs(Some(job.jobId))
        .map( jobList => {
          if (jobList.isEmpty)
            throw new NoSuchElementException("Job could not find with ID: " + job.jobId)

          // Suppose there should be only one job by jobId, just get the head of returned list
          val job2dispatch = jobList.head
          // TODO: Send notification by more types
          NotificationType.withName(job2dispatch.notificationType) match {
            case NotificationType.PUSH =>
              db.getUserTokensByLabelAndCountry(jobList.head.label, jobList.head.country).map {
                tokens => tokens.foreach { token =>
                  val caseToken = (JobToken.apply _).tupled(token)
                  Logger.debug("token: " + caseToken)
                  // TODO: Publish to job queue(Kafka) ready for consuming.
                }
              }
            case NotificationType.ALERT =>
              Logger.info("Coming soon...")
            case x =>
              Logger.info("[" + x + "] not support for scheduling now.")
          }
        } )
    case x =>
      Logger.warn("Unsupported message to dispatch: " + x)
  }

  private def handleRenewToken(renewToken: RenewToken) = {
    renewToken.tokenType match {
      case PushNotificationType.GCM =>
        Source.fromFuture[Int](db.updateGcmRegToken(renewToken.userId, renewToken.originalToken, renewToken.newToken))
      case PushNotificationType.APNS =>
        Source.fromFuture[Int](db.updateIosDeviceToken(renewToken.userId, renewToken.originalToken.getBytes, renewToken.newToken.getBytes))
      case _ =>
        Logger.warn("Unsupported token type: " + renewToken)
    }
  }

}

object PushNotificationProcessor {
  def props(config: Config)(implicit materializer: Materializer, fusionDb: FusionDatabase, keyspace: MarsKeyspace): Props =
    Props(new PushNotificationProcessor(config))
}