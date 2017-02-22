package me.mig.neith.utils

import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.Files.TemporaryFile
/**
  * Created by phyrextsai on 2017/2/10.
  */
class ImageUtilsTest extends FlatSpec with Matchers {

  val USER_ID = 195711006
  val FILE_PATH = "test/resources/test.jpeg"
  val TEMPFILE_PATH = "test/resources/file.jpeg"

  "An image calculated path" should "parse" in {
    val calculatePath = ImageUtils.calculatePath(USER_ID)
    println(s"CALCULATE_PATH: ${calculatePath}")
    calculatePath.matches("i\\/\\w{4}\\/\\w{36}\\/\\w{13}") should be(true)
  }

  "A test image" should "exists" in {
    val tempFile = TemporaryFile(new java.io.File("test/resources/test.jpeg"))
    tempFile.file.exists() should be(true)
  }

  "Copy file" should "copy" in {
    import java.io.{File,FileInputStream,FileOutputStream}
    val src = new File(FILE_PATH)
    val temp = new File(TEMPFILE_PATH)
    new FileOutputStream(temp) getChannel() transferFrom(
      new FileInputStream(src) getChannel, 0, Long.MaxValue )
    temp.exists() should be(true)
    temp.delete()
    temp.exists() should be(false)
  }
}
