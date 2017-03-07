package me.mig.neith.utils

import me.mig.neith.Config
import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by phyrextsai on 2017/2/21.
  */
class ConfigTest extends FlatSpec with Matchers {

  "User agent" should "display" in {
    Config.userAgent should be("neith/1.0")
  }
}
