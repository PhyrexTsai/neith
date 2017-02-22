package me.mig.neith.utils

import java.text.SimpleDateFormat
import java.util.Date

import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by phyrextsai on 2017/2/21.
  */
class DateTest extends FlatSpec with Matchers {

  "A date format" should "parse" in {
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    val time = simpleDateFormat.parse("2010-11-10T20:48:33.000Z").getTime
    val date = new Date(time)
    simpleDateFormat.format(date) should be("2010-11-10T20:48:33")
  }

}
