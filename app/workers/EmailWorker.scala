package workers

import akka.actor.{Actor, Props}
import play.api.Logger
import play.api.libs.mailer.{Email, MailerClient}

/**
  * Created by jameshsiao on 8/30/16.
  */
class EmailWorker (mailerClient: MailerClient) extends Actor {
  import workers.EmailWorker._

  override def receive: Receive = {
    case SendMail(subject, recipients, body) =>
      mailerClient.send(Email(
        subject,
        NOT_REPLY_SENDER,
        recipients,
        // sends text, HTML or both...
        //      bodyText = Some("A text message"),
        bodyHtml = Some(body)
      ))

    case x =>
      Logger.warn("Unsupported message: " + x)
  }

}

object EmailWorker {
  final val NOT_REPLY_SENDER = "Migme <donotreply@mig.me>"

  def props (mailerClient: MailerClient) : Props = Props(new EmailWorker(mailerClient))

  case class SendMail(subject: String, recipients: Seq[String], body: String)
}