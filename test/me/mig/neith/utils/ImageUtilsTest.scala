package me.mig.neith.utils

import org.scalatest.{FlatSpec, Matchers}
/**
  * Created by phyrextsai on 2017/2/10.
  */
class ImageUtilsTest extends FlatSpec with Matchers {

  val USER_ID = 195711006

  "An image calculated path" should "parse" in {
    val calculatePath = ImageUtils.calculatePath(USER_ID)
    println(s"CALCULATE_PATH: ${calculatePath}")
    calculatePath.matches("i\\/\\w{4}\\/\\w{36}\\/\\w{13}") should be(true)
  }
}
