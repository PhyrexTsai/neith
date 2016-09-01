package services

import javax.inject._

import akka.actor.ActorSystem
import org.apache.commons.mail.EmailException
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.{Configuration, Logger}
import play.twirl.api.Html
import workers.EmailWorker

import scala.concurrent.Future

/**
 * This class demonstrates how to run code when the
 * application starts and stops. It starts a timer when the
 * application starts. When the application stops it prints out how
 * long the application was running for.
 *
 * This class is registered for Guice dependency injection in the
 * [[Module]] class. We want the class to start when the application
 * starts, so it is registered as an "eager singleton". See the code
 * in the [[Module]] class to see how this happens.
 *
 * This class needs to run code when the server stops. It uses the
 * application's [[ApplicationLifecycle]] to register a stop hook.
 */
@Singleton
class EmailService @Inject()(system: ActorSystem, configuration: Configuration, appLifecycle: ApplicationLifecycle, mailerClient: MailerClient) {
  import EmailService._
  import EmailWorker._

  // Initialize workers
//  val emailWorkerPool = system.actorOf(BalancingPool(20).props(EmailWorker.props(mailerClient)), "EmailWorker")

  // This code is called when the application starts.
//  private val start: Instant = clock.instant
//  Logger.info(s"ApplicationTimer demo: Starting application at $start.")

  def sendVerifyEmail(toVerify: EmailVerification): SendEmailAck = {
    val emailVerifyTemplate = TemplateBackgroundService.get[(String, String) => Html]("html.emailVerify")
    if (emailVerifyTemplate == null) {
      SendEmailAck(error = Some("Can't load template now, please try again later."))
    } else {
      try {
        val response = mailerClient.send(Email(
          "Activate your migme account",
          NOT_REPLY_SENDER,
          Seq(toVerify.email),
          // sends text, HTML or both...
          //      bodyText = Some("A text message"),
          bodyHtml = Some(emailVerifyTemplate.static(toVerify.username, toVerify.verifyLink).toString().trim)
        ))
        if (!Some(response).isEmpty) {
          SendEmailAck(Some(true))
        } else {
          SendEmailAck(error = Some("Sending verification email failed."))
        }
      } catch {
        case ex: EmailException =>
          Logger.error("Mail server might be down: " + ex.getMessage)
          SendEmailAck(error = Some("Mail server might be down: " + ex.getMessage))
        case ex: Throwable =>
          Logger.error("Unknown exception: " + ex.getMessage)
          SendEmailAck(error = Some("Unknown exception: " + ex.getMessage))
      }
    }

//    val future = (emailWorkerPool ? SendMail("Activate your migme account", Seq(toVerify.email), emailVerifyTemplate.static(toVerify.username, toVerify.verifyLink).toString().trim))
//    Future.firstCompletedOf(Seq(future))
//      .map { response =>
//      response match {
//        case s: String =>
//          Logger.info("resposne: " + s)
//          Json.toJson[SendEmailResponse](SendEmailResponse(true))(sendEmailResponseWrites)
//        case x => Json.obj("error" -> JsString(x.toString))
//      }
//    }
  }

  // Send notification email

  // When the application starts, register a stop hook with the
  // ApplicationLifecycle object. The code inside the stop hook will
  // be run when the application stops.
  appLifecycle.addStopHook { () =>
//    val stop: Instant = clock.instant
//    val runningTime: Long = stop.getEpochSecond - start.getEpochSecond
//    Logger.info(s"ApplicationTimer demo: Stopping application at ${clock.instant} after ${runningTime}s.")
    Future.successful(())
  }
}

trait BaseResponse {
  val error: Option[String]
}

object EmailService {
  // Request
  case class EmailVerification(username: String, email: String, verifyLink: String)
  // Response
  case class SendEmailAck(isSuccess: Option[Boolean] = None, override val error: Option[String] = None) extends BaseResponse

  // Json reads
  implicit val emailVerificationReads = Json.reads[EmailVerification]
  // Json writes
//  implicit val jsonResponseWrites = Json.writes[JsonResponse]
  implicit val sendEmailResponseWrites = Json.writes[SendEmailAck]

  def JsonError(message: String): JsValue = {
    Json.obj("error" -> message)
  }
}