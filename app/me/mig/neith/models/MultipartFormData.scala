package me.mig.neith.models

import java.nio.file.Paths

import play.api.http.{HeaderNames, Writeable}
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{AnyContentAsMultipartFormData, Codec, MultipartFormData}
import play.api.mvc.MultipartFormData.FilePart

/**
  * Created by phyrextsai on 2017/2/14.
  */

/**
  * reference: https://groups.google.com/forum/embed/#!topic/play-framework/9wP9dx7bYSo
  */
object MultipartFormDataWritable {
  val boundary = "--------ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"

  def formatDataParts(data: Map[String, Seq[String]]) = {
    val dataParts = data.flatMap { case (key, values) =>
      values.map { value =>
        val name = s""""$key""""
        s"--$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: form-data; name=$name\r\n\r\n$value\r\n"
      }
    }.mkString("")
    Codec.utf_8.encode(dataParts)
  }

  def filePartHeader(file: FilePart[TemporaryFile]) = {
    val name = s""""${file.key}""""
    val filename = s""""${file.filename}""""
    val contentType = file.contentType.map { ct =>
      s"${HeaderNames.CONTENT_TYPE}: $ct\r\n"
    }.getOrElse("")
    Codec.utf_8.encode(s"--$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: form-data; name=$name; filename=$filename\r\n$contentType\r\n")
  }

  val singleton = Writeable[MultipartFormData[TemporaryFile]](
    transform = { form: MultipartFormData[TemporaryFile] =>
      formatDataParts(form.dataParts) ++
        form.files.flatMap { file =>
          val fileBytes = java.nio.file.Files.readAllBytes(Paths.get(file.ref.file.getAbsolutePath))
          filePartHeader(file) ++ fileBytes ++ Codec.utf_8.encode("\r\n")
        } ++
        Codec.utf_8.encode(s"--$boundary--")
    },
    contentType = Some(s"multipart/form-data; boundary=$boundary")
  )

  implicit val anyContentAsMultipartFormWritable: Writeable[AnyContentAsMultipartFormData] = {
    MultipartFormDataWritable.singleton.map(_.mdf)
  }
}
