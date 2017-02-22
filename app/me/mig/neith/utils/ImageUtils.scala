package me.mig.neith.utils

import org.apache.commons.codec.digest.DigestUtils

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
    val sb: StringBuffer = new StringBuffer
    sb.append(text.substring(0, 4))
    sb.append(delim)
    sb.append(text.substring(4))
    sb.toString
  }
}
