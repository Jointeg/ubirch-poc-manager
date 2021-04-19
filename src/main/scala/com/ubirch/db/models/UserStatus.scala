package com.ubirch.db.models
import io.getquill.MappedEncoding

sealed trait UserStatus
case object WaitingForRequiredActions extends UserStatus
case object WaitingForApproval extends UserStatus
case object UserVerified extends UserStatus

object UserStatus {
  implicit val encodeUserStatus: MappedEncoding[UserStatus, String] = MappedEncoding[UserStatus, String] {
    case WaitingForRequiredActions => "WAITING_FOR_REQUIRED_ACTIONS"
    case WaitingForApproval => "WAITING_FOR_APPROVAL"
    case UserVerified => "USER_VERIFIED"
  }
  implicit val decodeUserStatus: MappedEncoding[String, UserStatus] = MappedEncoding[String, UserStatus] {
    case "WAITING_FOR_REQUIRED_ACTIONS" => WaitingForRequiredActions
    case "WAITING_FOR_APPROVAL" => WaitingForApproval
    case "USER_VERIFIED" => UserVerified
  }
}
