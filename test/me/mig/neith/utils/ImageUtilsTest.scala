package me.mig.neith.utils

import java.io.File
import awscala.DateTime
import org.scalatest.{FlatSpec, Matchers}
import play.api.{Configuration, Environment}
import play.api.libs.Files.TemporaryFile
/**
  * Created by phyrextsai on 2017/2/10.
  */
class ImageUtilsTest extends FlatSpec with Matchers {

  private val bucketName = "images-staging.mig33.com"
  private val USER_ID = 195711006
  private val FILE_PATH = "test/resources/test.jpeg"
  private val TEMPFILE_PATH = "test/resources/template.jpeg"

  private val config = Configuration.load(Environment.simple(new File("test/resources/application.conf")))

  "An image calculated path" should "parse" in {
    val calculatePath = ImageUtils.calculatePath(USER_ID)
    println(s"CALCULATE PATH: ${calculatePath}")
    calculatePath.matches("i\\/\\w{4}\\/\\w{36}\\/\\w{13}") should be(true)
  }

  "A Pre-signed URL" should "generate" in {
    val imageUtils = new ImageUtils(config)
    val preSignedUrl = imageUtils.generatePreSignedUrl(bucketName, USER_ID, "migme.jpg", DateTime.now.plusMinutes(10))
    println(s"PRE-SIGNED URL: ${preSignedUrl}")
    preSignedUrl.getPath.matches("/i\\/\\w{4}\\/\\w{36}\\/\\w{13}") should be(true)
    preSignedUrl.getQuery.contains("Signature") should be(true)
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
