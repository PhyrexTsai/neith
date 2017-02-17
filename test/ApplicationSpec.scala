import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import me.mig.neith.models.MultipartFormDataWritable
import play.api.libs.json.Json

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  val USER_ID = "0"
  val FILE_NAME = "file.jpeg"
  val UPLOAD_ID = "sbXss9LSf90W7hOT_kjukU_8J16Q.enhYm0LuhL5JoZDbB4liDQZx4LNrRTZC6C2CR9UCTl8AGwpVhFDZiToO8st1ASsqI9L3aL47E8Qy_ZgdTl.aC0_3vmFNIuq3_Vt"
  val PART_UPLOAD_ID = "E4a4i59pyJ8zi83Qq6sf9ISSlVZGn4oJ_tiMLDLPiFtcTt0J8j9Nsn78XDLaH.GAThFoE6epecgNb22NIV6uWYW2S8e5MMFjHzH_VLSswoNwksJsfgwuPEvokh8vqxi8"

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

    "upload file to AWS S3 and response fileUrl on PUT /v1/users/:userId/upload" in new WithApplication {
      val tempFile = TemporaryFile(new java.io.File("/tmp/file.jpeg"))
      val part = FilePart[TemporaryFile](key = "file", filename = FILE_NAME, contentType = Some("image/jpeg"), ref = tempFile)
      val formData = MultipartFormData(dataParts = Map(), files = Seq(part), badParts = Seq())
      val body = new AnyContentAsMultipartFormData(formData)
      val request = FakeRequest(PUT, s"/v1/users/${USER_ID}/upload").withMultipartFormDataBody(formData)
      val result = route(request)(MultipartFormDataWritable.anyContentAsMultipartFormWritable).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("fileUrl")
    }

    "send file name and response uploadId on POST /v1/users/:userId/initiateMultipartUpload" in new WithApplication {
      val body = Json.obj(
        "fileName" -> "file.jpeg",
        "contentType" -> "image/jpeg"
      )
      val request = FakeRequest(POST, s"/v1/users/${USER_ID}/initiateMultipartUpload").withJsonBody(body)
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("fileName")
      contentAsString(result) must contain("uploadId")
    }

    "upload part file on POST /v1/users/:userId/uploadPart" in new WithApplication {
      val tempFile = TemporaryFile(new java.io.File("/tmp/file.jpeg"))
      val part = FilePart[TemporaryFile](key = "file", filename = FILE_NAME, contentType = Some("image/jpeg"), ref = tempFile)
      val formData = MultipartFormData(dataParts = Map(("fileName", Seq(FILE_NAME)), ("uploadId", Seq(PART_UPLOAD_ID)), ("dataPartNumber", Seq("1"))), files = Seq(part), badParts = Seq())
      val body = new AnyContentAsMultipartFormData(formData)
      val request = FakeRequest(POST, s"/v1/users/${USER_ID}/uploadPart").withMultipartFormDataBody(formData)
      val result = route(request)(MultipartFormDataWritable.anyContentAsMultipartFormWritable).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must contain("partNumber")
      contentAsString(result) must contain("eTag")  //fb10c14f71f8621cd2b0f3387da4cce9
    }

    "complete multipart upload on POST /v1/users/:userId/completeMultipartUpload" in new WithApplication {
      val body = Json.obj(
        "fileName" -> "file.jpeg",
        "uploadId" -> UPLOAD_ID
      )
      val request = FakeRequest(POST, s"/v1/users/${USER_ID}/completeMultipartUpload").withJsonBody(body)
//      val result = route(request).get
//
//      status(result) must equalTo(OK)
//      contentType(result) must beSome.which(_ == "application/json")
//      println("completeMultipartUpload.content: " + contentAsString(result))
//      contentAsString(result) must equalTo("{\"complete\":true}")
    }

    "abort multipart upload on DELETE /v1/users/:userId/abortMultipartUpload" in new WithApplication {
      val request = FakeRequest(DELETE, s"/v1/users/${USER_ID}/abortMultipartUpload?fileName=${FILE_NAME}&uploadId=${UPLOAD_ID}")
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      contentAsString(result) must equalTo("{\"abort\":true}")
    }

    "list multipart upload on GET /v1/users/:userId/listMultipartUploads" in new WithApplication {
      val request = FakeRequest(GET, s"/v1/users/${USER_ID}/listMultipartUploads?uploadId=${UPLOAD_ID}&fileName=${FILE_NAME}")
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      println("listMultipartUpload.content: " + contentAsString(result))
    }

    "get list part on GET /v1/users/:userId/listParts" in new WithApplication {
      val request = FakeRequest(GET, s"/v1/users/${USER_ID}/listParts?fileName=${FILE_NAME}&uploadId=${PART_UPLOAD_ID}")
      val result = route(request).get

      status(result) must equalTo(OK)
      contentType(result) must beSome.which(_ == "application/json")
      println("listPart.content: " + contentAsString(result))
    }
  }
}
