package me.mig.mars.services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import me.mig.mars.models.NotificationTemplateRepository
import me.mig.mars.workers.TemplateChecker
import me.mig.mars.workers.TemplateChecker.CompiledTemplate
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

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

  var templates = mutable.HashMap[String, EmailTemplate]()
  val templateConfig = configuration.underlying.getConfig("mars.templates")

  // Dynamically build templates @runtime
  // Start scheduler to check email templates if need to rebuild every 5 mins.
  val cancelable = system.scheduler.schedule(0 seconds, 300 seconds, system.actorOf(TemplateChecker.props(templateConfig, emailTemplateRepo, this)), ScheduledCheck)

  def get[T](className: String): (String, CompiledTemplate) = {
    val compiledTemplate = templates.get(templateClassPrefix + className)
    if (compiledTemplate isEmpty) ("", null)
    else {
      val templateObj = compiledTemplate.get
      (templateObj.subject, templateObj.template)
    }
  }

  def put(className: String, template: EmailTemplate): Unit = {
    templates.put(className, template)
  }

  def isTemplateUpdated(className: String, updatedTime: Long): Boolean = {
    templates.get(className) match {
      case Some(emailTemplate) =>
        updatedTime > emailTemplate.lastUpdated
      case None => true
    }
  }

  appLifecycle.addStopHook { () =>
    Future.successful(cancelable.cancel())
  }
}

case class EmailTemplate(subject: String, template: CompiledTemplate, lastUpdated: Long)