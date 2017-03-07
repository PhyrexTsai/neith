package me.mig.neith.utils

import awscala._
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.{CannedAccessControlList, GeneratePresignedUrlRequest}
import com.google.inject.Inject
import s3._
import org.apache.commons.codec.digest.DigestUtils
import play.api.Configuration

/**
  * Created by phyrextsai on 2017/2/8.
  */
object ImageUtils {

  val PHOTO_PATH_PREFIX: String = "i"

  val ALLOW_MIME_TYPE = Array(
    "image/bmp",
    "image/gif",
    "image/jpeg",
    "image/png",
    "image/svg+xml",
    "video/mpeg",
    "video/avi",
    "audio/aiff",
    "audio/midi",
    "audio/mpeg",
    "audio/mpeg3",
    "audio/wav"
    )

  // TODO the better way on unique photo id is using UUID and hash
  /**
    * Generate file path by user
    *
    * @param user
    * @return
    */
  def calculatePath(user: Int): String = String.format("%s/%s/%s", PHOTO_PATH_PREFIX, hashAndSplit(user.toString, "/", 4), System.currentTimeMillis().toString)

  private def hashAndSplit(rawText: String, delim: String, tokenSize: Int): String = {
    val text: String = DigestUtils.shaHex(rawText)
    val sb: StringBuilder = new StringBuilder
    sb.append(text.substring(0, 4))
    sb.append(delim)
    sb.append(text.substring(4))
    sb.toString
  }
}

class ImageUtils @Inject()(config: Configuration) {
  import awscala.Region
  private val AWS_ACCESSKEY_ID = config.getString("aws.accessKeyId").get
  private val AWS_SECRET_KEY = config.getString("aws.secretKey").get
  private val S3_REGION = config.getString("s3.region").get

  implicit val s3 = S3(AWS_ACCESSKEY_ID, AWS_SECRET_KEY)(Region.apply(S3_REGION))

  /**
    * Generate pre-signed url with acl: public-read
    * @param bucketName
    * @param userId
    * @param fileName
    * @param expiration
    * @return
    */
  def generatePreSignedUrl(bucketName: String, userId: Int, fileName: String, expiration: DateTime): java.net.URL = {
    val request = new GeneratePresignedUrlRequest(bucketName, ImageUtils.calculatePath(userId), HttpMethod.PUT);
    request.setExpiration(expiration.toDate)
    request.addRequestParameter("x-amz-acl", CannedAccessControlList.PublicRead.toString)
    s3.generatePresignedUrl(request)
  }
}