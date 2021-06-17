package com.ubirch.services

import java.util.UUID

sealed trait ServiceError
case class NotFound(id: UUID) extends ServiceError
