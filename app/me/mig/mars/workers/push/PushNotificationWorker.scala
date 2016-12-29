package me.mig.mars.workers.push

import javax.inject.Inject

import akka.actor.Actor
import com.typesafe.config.Config
import play.api.Logger

/**
  * Created by jameshsiao on 12/27/16.
  */
class PushNotificationWorker @Inject()(config: Config) extends Actor {

  override def receive: Receive = {
    case _ => Logger.warn("Unsupported event")
  }
}
