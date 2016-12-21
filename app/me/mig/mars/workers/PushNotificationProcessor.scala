package me.mig.mars.workers

import akka.actor.{Actor, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import me.mig.mars.event.MarsCommand.{PushNotificationType, RenewToken}
import me.mig.mars.models.FusionDatabase
import me.mig.mars.services.JobScheduleService.DispatchJob
import play.api.Logger

/**
  * Created by jameshsiao on 11/22/16.
  */
class PushNotificationProcessor(config: Config, db: FusionDatabase)(implicit val materializer: Materializer) extends Actor {

  override def receive: Receive = {
    case rt: RenewToken => handleRenewToken(rt)
    case job: DispatchJob =>
      Logger.info("Start dispatching job[Push Notification]...")
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
  def props(config: Config, db: FusionDatabase)(implicit materializer: Materializer): Props =
    Props(new PushNotificationProcessor(config, db))
}