package me.mig.neith.exceptions

import me.mig.neith.constants.ErrorCodes.ErrorCode

/**
  * Created by phyrextsai on 2017/1/23.
  */
case class NeithException(errorCode: Int, message: String) extends Exception(message)
