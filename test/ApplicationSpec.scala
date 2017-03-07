import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import me.mig.neith.models.MultipartFormDataWritable
import me.mig.neith.models.Files.PreSignedUploadResp
import org.specs2.specification.{AfterExample, BeforeExample}
import play.api.libs.json.Json

/**
 * Application test spec.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification with BeforeExample with AfterExample {

  val FILE_NAME = "file.jpeg"
  val PART_FILE_NAME = "part.jpeg"
  val MIME_TYPE = "image/jpeg"
  val UPLOAD_ID = "sbXss9LSf90W7hOT_kjukU_8J16Q.enhYm0LuhL5JoZDbB4liDQZx4LNrRTZC6C2CR9UCTl8AGwpVhFDZiToO8st1ASsqI9L3aL47E8Qy_ZgdTl.aC0_3vmFNIuq3_Vt"
  val PART_UPLOAD_ID = "gTZRV0LMJ.QFNimPPV4Oy4L4nxhg5TOfXe8Ho2EyY7C9AGj9AfnVwyG9jsEfiwWJJ1IdEOS5IamUPFROKoNd6hPz8QKomiEDuaoY1_ZPRZx1HICnP9sw7GY53wwHtamB"
  val ABORT_UPLOAD_ID = "G69Lk4NjASlga5EUrLyWHm0KKL3w3yCdsaA6okXoPvCXxktlZBF39XVySqjuN6u96wGNvEVJAU3iK4rKE_G9D8ip0i3bJ8G62GoZbgwMj4OlorYrD5BNmOFU2f0LNso."
  // Reference with "Working directory"
  val FILE_PATH = "test/resources/test.jpeg"
  val TEMP_FILE_PATH = "test/resources/file.jpeg"
  val PART_FILE_PATH = "test/resources/part.jpeg"
  val SESSION_ID = "SESSION_ID"

  override def before(): Unit = {
    import java.io.{File,FileInputStream,FileOutputStream}
    val file = new File(FILE_PATH)
    val tempFile = new File(TEMP_FILE_PATH)
    val partFile = new File(PART_FILE_PATH)
    new FileOutputStream(tempFile) getChannel() transferFrom(
      new FileInputStream(file) getChannel, 0, Long.MaxValue )
    new FileOutputStream(partFile) getChannel() transferFrom(
      new FileInputStream(file) getChannel, 0, Long.MaxValue )
  }

  override def after(): Unit = {
    import java.io.File
    val tempFile = new File(TEMP_FILE_PATH)
    tempFile.delete()
    val partFile = new File(PART_FILE_PATH)
    partFile.delete()
  }

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      val result = route(FakeRequest(GET, "/boum")).get
      status(result) must equalTo(NOT_FOUND)
    }

  }

  "Application HealthController" should {

    "send status OK on GET /health" in new WithApplication {
      val result = route(FakeRequest(GET, "/health")).get
      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must equalTo("{\"status\":\"OK\"}")
    }

  }

  "Application UsersController" should {

    "upload file to AWS S3 and response fileUrl on PUT /v1/files/upload" in new WithApplication {
      val tempFile = TemporaryFile(new java.io.File(TEMP_FILE_PATH))
      val part = FilePart[TemporaryFile](key = "file", filename = FILE_NAME, contentType = Some(MIME_TYPE), ref = tempFile)
      val formData = MultipartFormData(dataParts = Map(), files = Seq(part), badParts = Seq())
      val body = new AnyContentAsMultipartFormData(formData)
      val request = FakeRequest(PUT, s"/v1/files/upload").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      ).withMultipartFormDataBody(formData)
      val result = route(request)(MultipartFormDataWritable.anyContentAsMultipartFormWritable).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("fileUrl")
    }

    /**
      * Reference: http://stackoverflow.com/questions/10100193/put-file-to-s3-with-presigned-url
      * curl -k -v -T migme.jpg "https://images-staging.mig33.com.s3-us-west-1.amazonaws.com/i/44da/4220ace733d265a6a92fd1435573df123713/1488444291086?AWSAccessKeyId=AKIAJR35PVXZGYS6JRTA&Expires=1488444891&Signature=UPVMrSi9uF21lTT7C2NEF2nVlYc%3D"
      */
    "generate pre-signed url on POST /v1/files/preSignedUpload" in new WithApplication {
      val body = Json.obj(
        "fileName" -> FILE_NAME
      )
      val request = FakeRequest(POST, s"/v1/files/preSignedUpload").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      ).withJsonBody(body)
      val result = route(request).get

      val preSignedUrl = contentAsJson(result).as[PreSignedUploadResp].preSignedUrl
      println(preSignedUrl)
      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("preSignedUrl")
    }

    "upload empty file to AWS S3 and response BadRequest on PUT /v1/files/upload" in new WithApplication {
      val tempFile = TemporaryFile(new java.io.File(TEMP_FILE_PATH))
      val part = FilePart[TemporaryFile](key = "ErrorKey", filename = FILE_NAME, contentType = Some(MIME_TYPE), ref = tempFile)
      val formData = MultipartFormData(dataParts = Map(), files = Seq(part), badParts = Seq())
      val body = new AnyContentAsMultipartFormData(formData)
      val request = FakeRequest(PUT, s"/v1/files/upload").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      ).withMultipartFormDataBody(formData)
      val result = route(request)(MultipartFormDataWritable.anyContentAsMultipartFormWritable).get

      status(result) must equalTo(BAD_REQUEST)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("error")
    }

    "send file name and response uploadId on POST /v1/files/initiateMultipartUpload" in new WithApplication {
      val body = Json.obj(
        "fileName" -> FILE_NAME,
        "contentType" -> MIME_TYPE
      )
      val request = FakeRequest(POST, s"/v1/files/initiateMultipartUpload").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      ).withJsonBody(body)
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("fileName")
      contentAsString(result) must contain("uploadId")
      println("InitiateMultipartUpload: " + contentAsString(result))
    }

    "upload part file on POST /v1/files/uploadPart" in new WithApplication {
      val partFile = TemporaryFile(new java.io.File(PART_FILE_PATH))
      val part = FilePart[TemporaryFile](key = "file", filename = PART_FILE_NAME, contentType = Some(MIME_TYPE), ref = partFile)
      val formData = MultipartFormData(dataParts = Map(("fileName", Seq(FILE_NAME)), ("uploadId", Seq(PART_UPLOAD_ID)), ("dataPartNumber", Seq("1"))), files = Seq(part), badParts = Seq())
      val body = new AnyContentAsMultipartFormData(formData)
      val request = FakeRequest(POST, s"/v1/files/uploadPart").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      ).withMultipartFormDataBody(formData)
      val result = route(request)(MultipartFormDataWritable.anyContentAsMultipartFormWritable).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("partNumber")
      contentAsString(result) must contain("eTag")  //fb10c14f71f8621cd2b0f3387da4cce9
    }

    "complete multipart upload on POST /v1/files/completeMultipartUpload" in new WithApplication {
      val body = Json.obj(
        "fileName" -> FILE_NAME,
        "uploadId" -> UPLOAD_ID,
        "partUploadTickets" -> Json.arr(
          Json.obj(
            "partNumber" -> 1,
            "eTag" -> ""),
          Json.obj(
            "partNumber" -> 2,
            "eTag" -> "")
          )
        )
      val request = FakeRequest(POST, s"/v1/files/completeMultipartUpload").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      ).withJsonBody(body)
      val result = route(request).get

      status(result) must equalTo(BAD_REQUEST)
      contentType(result) must beSome.which(_ == "application/json")
      println("completeMultipartUpload.content: " + contentAsString(result))
      //contentAsString(result) must equalTo("{\"complete\":true}")
    }

    "abort multipart upload on DELETE /v1/files/abortMultipartUpload" in new WithApplication {
      val request = FakeRequest(DELETE, s"/v1/files/abortMultipartUpload?fileName=${FILE_NAME}&uploadId=${ABORT_UPLOAD_ID}").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      )
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must equalTo("{\"abort\":true}")
    }

    "list multipart upload on GET /v1/files/listMultipartUploads" in new WithApplication {
      val request = FakeRequest(GET, s"/v1/files/listMultipartUploads?uploadId=${UPLOAD_ID}&fileName=${FILE_NAME}").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      )
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      println("listMultipartUpload.content: " + contentAsString(result))
    }

    "get list part on GET /v1/files/listParts" in new WithApplication {
      val request = FakeRequest(GET, s"/v1/files/listParts?fileName=${FILE_NAME}&uploadId=${PART_UPLOAD_ID}").withHeaders(
        ("sessionId", SESSION_ID),
        ("x-forwarded-for", "8.8.8.8")
      )
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      println("listPart.content: " + contentAsString(result))
    }
  }
}
