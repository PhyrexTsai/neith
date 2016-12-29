import akka.routing.{BalancingPool, SmallestMailboxPool}
import com.google.inject.AbstractModule
import me.mig.mars.services.{EmailService, JobScheduleService, PushNotificationService, TemplateBackgroundService}
import me.mig.mars.workers.JobScheduleWorker
import me.mig.mars.workers.push.PushNotificationKafkaProducer
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.
  *
  * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends AbstractModule with AkkaGuiceSupport {

  override def configure() = {
    bindActor[JobScheduleWorker]("JobScheduleWorker", p => new BalancingPool(10).props(p))
    bindActor[PushNotificationKafkaProducer]("PushNotificationKafkaProducer", p => new SmallestMailboxPool(10).props(p))
    // Ask Guice to create an instance of TemplateBackgroundService when the
    // application starts.
    bind(classOf[TemplateBackgroundService]).asEagerSingleton()
    // Ask Guice to create an instance of EmailService and PushNotificationService when the
    // application starts.
    bind(classOf[EmailService]).asEagerSingleton()
    bind(classOf[PushNotificationService]).asEagerSingleton()
    bind(classOf[JobScheduleService]).asEagerSingleton()
  }

}
