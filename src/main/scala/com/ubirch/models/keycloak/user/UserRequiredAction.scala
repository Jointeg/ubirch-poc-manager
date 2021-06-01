package com.ubirch.models.keycloak.user

import org.keycloak.authentication.requiredactions.{ WebAuthnPasswordlessRegisterFactory, WebAuthnRegisterFactory }
import org.keycloak.models.UserModel.RequiredAction

/**
  * These enums are expansion of UserModel.RequiredAction enum as the original enum doesn't have some actions
  */
object UserRequiredAction extends Enumeration {
  val VERIFY_EMAIL: Value = Value(RequiredAction.VERIFY_EMAIL.toString)
  val UPDATE_PROFILE: Value = Value(RequiredAction.UPDATE_PROFILE.toString)
  val CONFIGURE_TOTP: Value = Value(RequiredAction.CONFIGURE_TOTP.toString)
  val UPDATE_PASSWORD: Value = Value(RequiredAction.UPDATE_PASSWORD.toString)
  val TERMS_AND_CONDITIONS: Value = Value(RequiredAction.TERMS_AND_CONDITIONS.toString)
  val WEBAUTHN_PASSWORDLESS_REGISTER: Value = Value(WebAuthnPasswordlessRegisterFactory.PROVIDER_ID)
  val WEBAUTHN_REGISTER: Value = Value(WebAuthnRegisterFactory.PROVIDER_ID)
}