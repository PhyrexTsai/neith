package services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import models.NotificationTemplateRepository
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import workers.TemplateChecker

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Created by jameshsiao on 8/29/16.
  */

@Singleton
class TemplateBackgroundService @Inject()(system: ActorSystem, configuration: Configuration, appLifecycle: ApplicationLifecycle, emailTemplateRepo: NotificationTemplateRepository) {
  import TemplateChecker._
  import system.dispatcher

  import scala.concurrent.duration._

  var templates = mutable.HashMap[String, (Any, Long)]()
  val templateConfig = configuration.underlying.getConfig("mars.templates")

  // Dynamically build templates @runtime
  // Start scheduler to check email templates if need to rebuild every 5 mins.
  val cancelable = system.scheduler.schedule(0 seconds, 300 seconds, system.actorOf(TemplateChecker.props(templateConfig, emailTemplateRepo, this)), ScheduledCheck)

  def get[T](className: String): CompiledTemplate[T] = {
    val compiledTemplate = templates.get(templateClassPrefix + className)
    if (compiledTemplate isEmpty) null
    else compiledTemplate.get._1.asInstanceOf[CompiledTemplate[T]]
  }
  def put(className: String, template: Any, lastUpdated: Long): Unit = {
    templates.put(className, (template, lastUpdated))
  }

  def isTemplateUpdated(className: String, updatedTime: Long): Boolean = {
    templates.get(className) match {
      case Some((template, lastUpdated)) =>
        updatedTime > lastUpdated
      case None => true
    }
  }

  appLifecycle.addStopHook { () =>
    Future.successful(cancelable.cancel())
  }
}
