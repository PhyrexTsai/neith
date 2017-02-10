package me.mig.neith.utils

import org.scalatest.{FlatSpec, Matchers}
/**
  * Created by phyrextsai on 2017/2/10.
  */
class ImageUtilsTest extends FlatSpec with Matchers {
  "An image calculated path" should "parse" in {
    val calculatePath = ImageUtils.calculatePath(195711006)
    calculatePath.matches("i\\/\\w{4}\\/\\w{36}\\/\\w{13}") should be("i/a5c7/37030cb4c77ed1bf42ede5e7377a50234675/1486715943125")
  }
}
