package me.mig.mars.workers.push

import java.util.HashMap
import java.util.regex.{Matcher, Pattern}
import javax.inject.Inject

import akka.actor.Actor
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model._
import me.mig.mars.models.JobModel.PushJob
import org.apache.commons.codec.binary.Hex
import play.api.libs.json.{JsString, Json}
import play.api.{Configuration, Logger}

/**
  * Created by jameshsiao on 12/27/16.
  */
class PushNotificationWorker @Inject()(configuration: Configuration) extends Actor {
  private val snsClient = new AmazonSNSClient(
    new BasicAWSCredentials(
      configuration.getString(PushNotificationWorker.ACCESS_KEY).getOrElse(""),
      configuration.getString(PushNotificationWorker.SECRET).getOrElse("")
    )
  )
  snsClient.setRegion(Region.getRegion(Regions.fromName(configuration.getString(PushNotificationWorker.REGION).getOrElse(""))))
  snsClient.setEndpoint(configuration.getString(PushNotificationWorker.ENDPOINT).getOrElse(""))
  private val gcmApplicationArn = configuration.getString(PushNotificationWorker.GCM_ARN)
  private val apnsApplicationArn = configuration.getString(PushNotificationWorker.APNS_ARN)
  if (gcmApplicationArn isEmpty) throw new NoSuchFieldException("Can't find any configuration for GCM application arn.")
  if (apnsApplicationArn isEmpty) throw new NoSuchFieldException("Can't find any configuration for APNS application arn.")

  private def createPlatformEndpoint(platformToken: String, applicationArn: String): String = {
    var endpointArn: String = null
    try {
      val req = new CreatePlatformEndpointRequest()
        .withPlatformApplicationArn(applicationArn)
        .withToken(platformToken)
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

      updateNeeded = !geaRes.getAttributes().get("Token").equals(platformToken) ||
        !geaRes.getAttributes().get("Enabled").equalsIgnoreCase("true")

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
      val attribs: HashMap[String, String] = new HashMap()
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
      if (pushJob.gcmToken nonEmpty) {
        val gcmEndpoint = registerWithSns(pushJob.gcmToken.get, gcmApplicationArn.get)
        publish(PushNotificationWorker.toGcmMessage(pushJob.message), gcmEndpoint)
      }
      if (pushJob.iosToken nonEmpty) {
        val apnsEndpoint = registerWithSns(Hex.encodeHexString(pushJob.iosToken.get), apnsApplicationArn.get)
        publish(PushNotificationWorker.toApnsMessage(pushJob.message), apnsEndpoint)
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

  def toGcmMessage(message: String): String = {
    Json.obj(
      "GCM" -> JsString(
        {"data" -> Json.obj(
          "message" -> Json.toJson(message)
        )}.toString()
      )
    ).toString()
  }

  def toApnsMessage(message: String): String = {
    Json.obj(
      "APNS" -> JsString(
        {"aps" -> Json.obj(
          "alert" -> Json.toJson(message)
        )}.toString()
      )
    ).toString()
  }
}