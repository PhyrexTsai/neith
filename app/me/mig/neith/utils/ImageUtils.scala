package me.mig.neith.utils

import org.apache.commons.codec.digest.DigestUtils

/**
  * Created by phyrextsai on 2017/2/8.
  */
object ImageUtils {

  val PHOTO_PATH_PREFIX: String = "i"

  // TODO the better way on unique photo id is using UUID and hash
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
