package me.mig.mars.services

import javax.inject._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import me.mig.mars.BaseResponse
import me.mig.mars.models.NotificationMappings._
import org.apache.commons.mail.EmailException
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.{Email, MailerClient}

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
class EmailService @Inject()(system: ActorSystem, appLifecycle: ApplicationLifecycle, templateBackgroundService: TemplateBackgroundService, mailerClient: MailerClient) {
  import me.mig.mars.services.EmailService._

  def sendVerifyEmail(toVerify: EmailVerification): SendEmailAck = {
    sendEmailByTemplate(EMAIL_VERIFICATION, List(toVerify.username, toVerify.verifyLink), Seq(toVerify.email))
  }

  def sendForgotPasswordEmail(toReset: ForgotPassword): SendEmailAck = {
    sendEmailByTemplate(FORGOT_PASSWORD_EMAIL, List(toReset.username, toReset.resetLink), Seq(toReset.email))
  }

  def sendEmailByTemplate(templateType: NotificationMappings, parameters: List[String], recipients: Seq[String]): SendEmailAck = {
    val (subject, emailTemplate) = templateBackgroundService.get(templateType.toString)
    if (emailTemplate == null) {
      SendEmailAck(error = Some("Can't load template now, please try again later."))
    } else {
      try {
        val response = mailerClient.send(Email(
          subject,
          NOT_REPLY_SENDER,
          recipients,
          // sends text, HTML or both...
          //      bodyText = Some("A text message"),
          bodyHtml = Some(emailTemplate.static(parameters).toString().trim)
        ))
        if (!Some(response).isEmpty) {
          SendEmailAck(Some(true))
        } else {
          val errorMessage = templateType match {
            case EMAIL_VERIFICATION => "Sending verification email failed."
            case FORGOT_PASSWORD_EMAIL => "Sending forgot password email failed."
            case _ => "Sending email failed (type: " + templateType.toString + ")."
          }
          SendEmailAck(error = Some(errorMessage))
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
  }

  // When the application starts, register a stop hook with the
  // ApplicationLifecycle object. The code inside the stop hook will
  // be run when the application stops.
  appLifecycle.addStopHook { () =>
    Future.successful(())
  }
}



object EmailService {
  final val NOT_REPLY_SENDER = "Migme <donotreply@mig.me>"

  // Request
  case class EmailVerification(username: String, email: String, verifyLink: String)
  case class ForgotPassword(username: String, email: String, resetLink: String)
  // Response
  case class SendEmailAck(isSuccess: Option[Boolean] = None, override val error: Option[String] = None) extends BaseResponse

  // Json reads
  implicit val emailVerificationReads = Json.reads[EmailVerification]
  implicit val forgotPasswordReads = Json.reads[ForgotPassword]
  // Json writes
  implicit val sendEmailResponseWrites = Json.writes[SendEmailAck]

  // TODO: Could extract to be the common util.
  def serializeToJsonResponse: Flow[BaseResponse, (Int, JsValue), _] = Flow[BaseResponse].map { response =>
    val statusCode = if (response.error.isEmpty) 200 else 400
    response match {
      case r: SendEmailAck =>
        Logger.info("sendemail response: " + r)
        (statusCode, Json.toJson[SendEmailAck](r)(sendEmailResponseWrites))
    }
  }
}
