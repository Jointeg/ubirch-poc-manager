package com.ubirch.models.keycloak.user

import org.keycloak.authentication.requiredactions.{ WebAuthnPasswordlessRegisterFactory, WebAuthnRegisterFactory }
import org.keycloak.models.UserModel.RequiredAction

/**
  * These enums are expansion of UserModel.RequiredAction enum as the original enum doesn't have some actions
  */
object UserRequiredAction extends Enumeration {
  val VERIFY_EMAIL = Value(RequiredAction.VERIFY_EMAIL.toString)
  val UPDATE_PROFILE = Value(RequiredAction.UPDATE_PROFILE.toString)
  val CONFIGURE_TOTP = Value(RequiredAction.CONFIGURE_TOTP.toString)
  val UPDATE_PASSWORD = Value(RequiredAction.UPDATE_PASSWORD.toString)
  val TERMS_AND_CONDITIONS = Value(RequiredAction.TERMS_AND_CONDITIONS.toString)
  val WEBAUTHN_PASSWORDLESS_REGISTER: UserRequiredAction.Value = Value(WebAuthnPasswordlessRegisterFactory.PROVIDER_ID)
  val WEBAUTHN_REGISTER = Value(WebAuthnRegisterFactory.PROVIDER_ID)
}
