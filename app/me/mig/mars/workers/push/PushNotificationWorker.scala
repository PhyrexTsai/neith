package me.mig.mars.workers.push

import java.util
import java.util.regex.{Matcher, Pattern}
import javax.inject.Inject

import akka.actor.Actor
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model._
import me.mig.mars.models.JobModel.PushJob
import me.mig.mars.workers.push.PushNotificationWorker._
import org.apache.commons.codec.binary.Hex
import play.api.libs.json._
import play.api.{Configuration, Logger}

/**
  * Created by jameshsiao on 12/27/16.
  */
class PushNotificationWorker @Inject()(configuration: Configuration) extends Actor {
  private val snsClient = new AmazonSNSClient(
    new BasicAWSCredentials(
      configuration.getString(ACCESS_KEY).getOrElse(""),
      configuration.getString(SECRET).getOrElse("")
    )
  )
  snsClient.setRegion(Region.getRegion(Regions.fromName(configuration.getString(REGION).getOrElse(""))))
  snsClient.setEndpoint(configuration.getString(ENDPOINT).getOrElse(""))
  private val gcmApplicationArn = configuration.getString(GCM_ARN)
  private val apnsApplicationArn = configuration.getString(APNS_ARN)
  if (gcmApplicationArn.isEmpty) throw new NoSuchFieldException("Can't find any configuration for GCM application arn.")
  if (apnsApplicationArn.isEmpty) throw new NoSuchFieldException("Can't find any configuration for APNS application arn.")

  private def createPlatformEndpoint(platformToken: String, applicationArn: String): String = {
    var endpointArn: String = null
    try {
      val req = new CreatePlatformEndpointRequest()
        .withPlatformApplicationArn(applicationArn)
        .withToken(platformToken)
        .withCustomUserData("CustomData - Useful to store endpoint specific data")  // Try to resolve the different attribute with same token issue.
      val endpointResult = snsClient.createPlatformEndpoint(req)
      endpointArn = endpointResult.getEndpointArn
    } catch {
      case ipe: InvalidParameterException =>
        Logger.warn("Create endpoint error caught InvalidParameterException: " + ipe.getErrorMessage)
        val p: Pattern = Pattern
          .compile(".*Endpoint (arn:aws:sns[^ ]+) already exists " +
            "with the same token.*")
        val m: Matcher = p.matcher(ipe.getErrorMessage)
        if (m.matches()) {
          // The platform endpoint already exists for this token, but with
          // additional custom data that
          // createEndpoint doesn't want to overwrite. Just use the
          // existing platform endpoint.
          endpointArn = m.group(1)
        } else {
          // Rethrow the exception, the input is actually bad.
          throw ipe
        }
    }

    endpointArn
  }

  private def registerWithSns(platformToken: String, platformApplicationArn: String): String = {
    var updateNeeded = false
    var createNeeded = false
    var endpointArn: String = createPlatformEndpoint(
//      "CustomData - Useful to store endpoint specific data",
      platformToken, platformApplicationArn)

    try {
      Logger.warn("getEndpointAttributesRequest")
      val geaReq: GetEndpointAttributesRequest =
        new GetEndpointAttributesRequest()
          .withEndpointArn(endpointArn)
      val geaRes: GetEndpointAttributesResult =
        snsClient.getEndpointAttributes(geaReq)

      updateNeeded = !geaRes.getAttributes.get("Token").equals(platformToken) ||
        !geaRes.getAttributes.get("Enabled").equalsIgnoreCase("true")

    } catch {
      case nfe: NotFoundException =>
        // We had a stored ARN, but the platform endpoint associated with it
        // disappeared. Recreate it.
        createNeeded = true
    }

    if (createNeeded) {
      endpointArn = createPlatformEndpoint(
//        "CustomData - Useful to store endpoint specific data",
        platformToken, platformApplicationArn)
    }

    if (updateNeeded) {
      // The platform endpoint is out of sync with the current data;
      // update the token and enable it.
      Logger.info("Updating platform endpoint " + endpointArn)
      val attribs: util.HashMap[String, String] = new util.HashMap()
      attribs.put("Token", platformToken)
      attribs.put("Enabled", "true")
      val saeReq: SetEndpointAttributesRequest =
        new SetEndpointAttributesRequest()
          .withEndpointArn(endpointArn)
          .withAttributes(attribs)
      snsClient.setEndpointAttributes(saeReq)
    }

    endpointArn
  }

  private def publish(message: String, endpointArn: String) : PublishResult = {
    Logger.warn("publish")
    val publishRequest = new PublishRequest()
//    val notificationAttributes = getValidNotificationAttributes(attributesMap.get(platform).get);
//    if (notificationAttributes != null && !notificationAttributes.isEmpty()) {
//      publishRequest.setMessageAttributes(notificationAttributes);
//    }
    Logger.debug("endpointArn: " + endpointArn)
    Logger.debug("message: " + message)
    publishRequest.setMessageStructure("json")
    // For direct publish to mobile end points, topicArn is not relevant.
    publishRequest.setTargetArn(endpointArn)
    publishRequest.setMessage(message)
    Logger.debug("request: " + publishRequest)
    snsClient.publish(publishRequest)
  }

  override def receive: Receive = {
    case pushJob: PushJob =>
      Logger.info("Push Job: " + pushJob)
      // TODO: Error handling for Option value
      // TODO: compile message template with given parameters??
      try {
        if (pushJob.gcmToken.nonEmpty) {
          val gcmEndpoint = registerWithSns(pushJob.gcmToken.get, gcmApplicationArn.get)
          publish(toGcmMessage(generateCallToAction(pushJob.callToAction.getOrElse(Map("type" -> "post"))), pushJob.message, pushJob.userId, pushJob.username.getOrElse("")), gcmEndpoint)
        }
        if (pushJob.iosToken.nonEmpty) {
          val apnsEndpoint = registerWithSns(Hex.encodeHexString(pushJob.iosToken.get), apnsApplicationArn.get)
          publish(toApnsMessage(generateCallToAction(pushJob.callToAction.getOrElse(Map("type" -> "post"))), pushJob.message, pushJob.userId, pushJob.username.getOrElse("")), apnsEndpoint)
        }
      }
      catch {
        case ex: EndpointDisabledException =>
          Logger.warn("Disabled endpoint: " + ex.getMessage + ", should be removed??")
      }
    case _ => Logger.warn("Unsupported event")
  }
}

object PushNotificationWorker {
  private final val BASE_KEY = "push.sns"

  final val ACCESS_KEY = BASE_KEY + ".accessKey"
  final val SECRET = BASE_KEY + ".secret"
  final val REGION = BASE_KEY + ".region"
  final val ENDPOINT = BASE_KEY + ".endpoint"

  final val GCM_ARN = "push.gcm.snsArn"
  final val APNS_ARN = "push.apns.snsArn"

  // TODO: Might be common function ?
  // TODO: Create Enumeration
  def generateCallToAction(action: Map[String, String]): String = {
    val value = action.get("value").getOrElse("")
    action.getOrElse("type", "").toLowerCase match {
      case "post" =>  s"mig33:showPost('${ value }', '0')"
      case "profile" => s"mig33:profile('${ value }')"
      case "link" => s"mig33:url('${ value }')"
      case "chatroom" => s"mig33:joinChatroom('${ value }')"
      case x =>
        Logger.warn("Unsupported action type: " + x)
        throw new IllegalArgumentException("Unsupported action type: " + x)
    }
  }

  def toGcmMessage(action: String, message: String, userId: Int, username : String): String = {
    Json.obj(
      "GCM" -> Json.obj(
        "data" -> Json.obj(
          "message" -> Json.obj(
            "_version" -> "2.0",
            "timestamp" -> System.currentTimeMillis(),
            "image" -> Json.obj("title " -> ""),  // might be unused
            "isBatched" -> false,  // might be unused
            "variables" -> Json.arr(Json.obj("name" -> "author")), // might be unused
            "id" -> System.currentTimeMillis(),
            "actions" -> Json.arr(
              Json.obj(
                "type" -> "URL",  // might be unused
                "label" -> Json.obj("text" -> "View Now"),  // might be unused
                "url" -> Json.arr(  // should be flatten ?
                  Json.obj(
                    "view" -> "touch",
                    "url" -> action
                  )
                )
              )
            ),
            "message" -> message,
            "title" -> "You've been migged!",
            "type" -> "SYS_ALERT",  // might be unused
            "user" -> Json.obj(
              "username" -> username,
              "id" -> userId
            )
          )
        )
      ).toString()
    ).toString()
  }

  def toApnsMessage(action: String, message: String, userId: Int, username : String): String = {
    Json.obj(
      "APNS" -> Json.obj(
        "aps" -> Json.obj(
          "alert" -> Json.obj(
            "body" -> message
          )
        ),
        /* Old verson body, will removed after old versions are deprecated */
        "_version" -> "2.0",
        "timestamp" -> System.currentTimeMillis(),
        "image" -> Json.obj("title " -> ""),  // might be unused
        "isBatched" -> false,  // might be unused
        "variables" -> Json.arr(Json.obj("name" -> "author")), // might be unused
        "id" -> System.currentTimeMillis(),
        "actions" -> Json.arr(
          Json.obj(
            "type" -> "URL",  // might be unused
            "label" -> Json.obj("text" -> "View Now"),  // might be unused
            "url" -> Json.arr(  // should be flatten ?
              Json.obj(
                "view" -> "touch",
                "url" -> action
              )
            )
          )
        ),
        "title" -> "You've been migged!",
        "type" -> "SYS_ALERT",  // might be unused
        "user" -> Json.obj(
          "username" -> username,
          "id" -> userId
        ),
        /* New version body */
        "alertAction" -> action
      ).toString()
    ).toString()
  }
}