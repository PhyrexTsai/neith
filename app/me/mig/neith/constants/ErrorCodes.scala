package me.mig.neith.constants

/**
  * Created by phyrextsai on 2017/1/23.
  */
object ErrorCodes extends Enumeration {
  val FILE_NOT_FOUND = ErrorCode(10001, "Upload failed, file not found.")
  val UNSUPPORTED_MIME_TYPE = ErrorCode(10002, "Unsupported MIME type.")
  val UNKNOWN_ERROR = ErrorCode(99999, "Unknown error.")

  protected case class ErrorCode(errorCode: Int, message: String) extends super.Val()
  implicit def convert(value: Value) = value.asInstanceOf[ErrorCode]
}
