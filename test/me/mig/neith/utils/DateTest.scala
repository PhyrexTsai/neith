package me.mig.neith.utils

import java.time.format.DateTimeFormatter
import java.util.Date

import org.apache.commons.lang3.time.FastDateFormat
import org.joda.time.format.DateTimeFormat
import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by phyrextsai on 2017/2/21.
  */
class DateTest extends FlatSpec with Matchers {

  "A date format" should "parse" in {
    val format = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val date = new Date(format.parseMillis("2010-11-10T20:48:33.000Z"))
    format.print(date.getTime) should be("2010-11-10T20:48:33.000Z")
  }

}
