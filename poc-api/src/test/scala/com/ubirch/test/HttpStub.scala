package com.ubirch.test

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import sttp.model.MediaType.ApplicationOctetStream
import sttp.model.{ HeaderNames, MediaType }

import scala.concurrent.duration._

class HttpStub(wiremock: WireMockServer, val url: String, charset: String = "UTF-8") { self =>

  import HttpStub._

  private val ApplicationJson: String = MediaType.ApplicationJson.charset(charset).toString()

  def kill(): Unit = wiremock.stop()

  def spaceWillBeCreated(
    username: String = TestData.username,
    password: String = TestData.password,
    spaceName: String = TestData.spaceName,
    spacePath: String = TestData.spacePath,
    spaceId: Int = TestData.spaceId
  ): HttpStub = {
    wiremock.stubFor(
      post(urlEqualTo("/api/createSpace"))
        .withBasicAuth(username, password)
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .withRequestBody(matchingJsonPath("spaceName", equalTo(spaceName)))
        .withRequestBody(matchingJsonPath("spacePath", equalTo(spacePath)))
        .withRequestBody(matchingJsonPath("disableFileSystem", equalTo("false")))
        .withRequestBody(matchingJsonPath("webAccess", equalTo("true")))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(200)
            .withBody(createSpaceOkResponse(creator = username, spaceId = spaceId))
        )
    )
    self
  }

  def fileWillBeSent(
    username: String = TestData.username,
    password: String = TestData.password,
    spaceId: Int = TestData.spaceId,
    fileBody: Array[Byte],
    fileName: String,
    fileId: Int
  ): HttpStub = {
    wiremock.stubFor(
      put(urlEqualTo(s"/files/$spaceId/$fileName"))
        .withBasicAuth(username, password)
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationOctetStream.toString()))
        .withRequestBody(binaryEqualTo(fileBody))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(200)
            .withBody(putFileOkResponse(spaceId = spaceId, fileId = fileId, fileName = fileName))
        )
    )
    self
  }

  def invitationWillBeAccepted(
    username: String = TestData.username,
    password: String = TestData.password,
    spaceId: Int = TestData.spaceId,
    email: String = TestData.email,
    permissionLevel: String = TestData.defaultPermissionLevel
  ): HttpStub = {
    wiremock.stubFor(
      post(urlEqualTo("/api/inviteMember"))
        .withBasicAuth(username, password)
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .withRequestBody(matchingJsonPath("spaceId", equalTo(s"$spaceId")))
        .withRequestBody(matchingJsonPath("permissionLevel", equalTo(permissionLevel)))
        .withRequestBody(matchingJsonPath("sendEmail", equalTo("true")))
        .withRequestBody(matchingJsonPath("name", equalTo(email)))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(200)
            .withBody(inviteMemberOkResponse)
        )
    )
    self
  }

  def createSpaceWillFail(
    username: String = TestData.username,
    password: String = TestData.password,
    errorCode: Int,
    errorMessage: String,
    statusCode: Int
  ): HttpStub = {
    wiremock.stubFor(
      post(urlEqualTo("/api/createSpace"))
        .withBasicAuth(username, password)
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(statusCode)
            .withBody(errorResponse(message = errorMessage, statusCode = statusCode, errorCode = errorCode))
        )
    )
    self
  }

  def putFileWillFail(
    username: String = TestData.username,
    password: String = TestData.password,
    spaceId: Int = TestData.spaceId,
    fileName: String,
    errorCode: Int,
    errorMessage: String,
    statusCode: Int
  ): HttpStub = {
    wiremock.stubFor(
      put(urlEqualTo(s"/files/$spaceId/$fileName"))
        .withBasicAuth(username, password)
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationOctetStream.toString()))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(statusCode)
            .withBody(errorResponse(message = errorMessage, statusCode = statusCode, errorCode = errorCode))
        )
    )
    self
  }

  def invitationWillFail(
    username: String = TestData.username,
    password: String = TestData.password,
    spaceId: Int = TestData.spaceId,
    email: String = TestData.email,
    permissionLevel: String = TestData.defaultPermissionLevel,
    errorCode: Int,
    errorMessage: String,
    statusCode: Int
  ): HttpStub = {
    wiremock.stubFor(
      post(urlEqualTo("/api/inviteMember"))
        .withBasicAuth(username, password)
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .withRequestBody(matchingJsonPath("spaceId", equalTo(s"$spaceId")))
        .withRequestBody(matchingJsonPath("permissionLevel", equalTo(permissionLevel)))
        .withRequestBody(matchingJsonPath("sendEmail", equalTo("true")))
        .withRequestBody(matchingJsonPath("name", equalTo(email)))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(statusCode)
            .withBody(errorResponse(message = errorMessage, statusCode = statusCode, errorCode = errorCode))
        )
    )
    self
  }

  def getLoginInformationWillReturn(isLoginRequired: Boolean): HttpStub = {
    wiremock.stubFor(
      get(urlEqualTo("/getLoginInformation"))
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(200)
            .withBody(getLoginInformationResponse(isLoginRequired = isLoginRequired))
        )
    )
    self
  }

  def getLoginInformationWillFail(
    errorCode: Int,
    errorMessage: String,
    statusCode: Int): HttpStub = {
    wiremock.stubFor(
      get(urlEqualTo("/getLoginInformation"))
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(statusCode)
            .withBody(errorResponse(errorMessage, statusCode, errorCode))
        )
    )
    self
  }

  def loginWillBeOk(username: String = TestData.username, password: String = TestData.password): HttpStub = {
    wiremock.stubFor(
      post(urlEqualTo("/login"))
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .withRequestBody(matchingJsonPath("username", equalTo(username)))
        .withRequestBody(matchingJsonPath("password", equalTo(password)))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(200)
            .withBody(loginResponse(username))
        )
    )
    self
  }

  def loginWillFail(
    username: String = TestData.username,
    password: String = TestData.password,
    errorCode: Int,
    errorMessage: String,
    statusCode: Int): HttpStub = {
    wiremock.stubFor(
      post(urlEqualTo("/login"))
        .withHeader(HeaderNames.ContentType, equalTo(ApplicationJson))
        .withRequestBody(matchingJsonPath("username", equalTo(username)))
        .withRequestBody(matchingJsonPath("password", equalTo(password)))
        .willReturn(
          aResponse()
            .withHeader(HeaderNames.ContentType, ApplicationJson)
            .withStatus(statusCode)
            .withBody(errorResponse(errorMessage, statusCode, errorCode))
        )
    )
    self
  }

  def anyRequestWillTimeout(delay: Duration): HttpStub = {
    val response = aResponse()
      .withFixedDelay(delay.toMillis.toInt)
      .withStatus(400)
      .withBody(errorResponse(message = "errorMessage", statusCode = 400, errorCode = 31))

    wiremock.stubFor(post(anyUrl()).willReturn(response))
    wiremock.stubFor(put(anyUrl()).willReturn(response))
    wiremock.stubFor(get(anyUrl()).willReturn(response))
    self
  }

  def loginWasNotCalled(): HttpStub = {
    wiremock.verify(exactly(0), postRequestedFor(urlEqualTo("/login")))
    self
  }

  def loginWasCalled(): HttpStub = {
    wiremock.verify(exactly(1), postRequestedFor(urlEqualTo("/login")))
    self
  }
}

object HttpStub {
  def createSpaceOkResponse(creator: String, spaceId: Int): String =
    s"""{
       | "result": true,
       | "space": {
       |   "creator": "$creator",
       |   "currentSnapshotId": 0,
       |   "dataRetentionDeleteGuard": false,
       |   "dataRetentionEnabled": false,
       |   "depotId": "57365A002C6CAD6B33B99E1C63BD87F4",
       |   "fileId": 3,
       |   "hasComments": false,
       |   "hasTrashedFiles": false,
       |   "icon": "space",
       |   "id": $spaceId,
       |   "name": "test-space",
       |   "newComments": 0,
       |   "permissionLevel": "administrator",
       |   "readConfirmationMode": 0,
       |   "status": "active",
       |   "status_message": "OK",
       |   "supportsReadConfirmations": true,
       |   "supportsSnapshots": true,
       |   "time": "2021-05-14T06:41:58.491Z",
       |   "virtualSpace": false,
       |   "webAccessAllowed": true
       | },
       | "spaceId": $spaceId
       |}""".stripMargin

  def errorResponse(message: String, statusCode: Int, errorCode: Int): String =
    s"""{
       | "error": $errorCode,
       | "error_message": "$message"
       | "result": false,
       | "status_code": $statusCode
       |}""".stripMargin

  def putFileOkResponse(spaceId: Int, fileId: Int, fileName: String): String =
    s"""{
       | "file": {
       |   "confirmed": false,
       |   "created": "2021-05-17T08:44:25.272Z",
       |   "creator": "username",
       |   "currentVersionId": 0,
       |   "hasComments": false,
       |   "hasNameConflict": false,
       |   "hasPublishedVersions": false,
       |   "hasVersionConflict": false,
       |   "icon": "default",
       |   "id": $fileId,
       |   "isDir": false,
       |   "isLockedByMe": false,
       |   "isLockedByOther": false,
       |   "isOfflineArchived": false,
       |   "isTemporaryPinned": false,
       |   "lastModified": "2021-05-17T08:44:25.272Z",
       |   "name": "$fileName",
       |   "path": "/",
       |   "permissions": "rw-r--r--",
       |   "progress": -2.0,
       |   "size": 11,
       |   "spaceId": $spaceId,
       |   "time": "2021-05-17T08:44:25.272Z",
       |   "type": "application/octet-stream"
       | },
       | "newVersionId": 2,
       | "result": true
       |}""".stripMargin

  def inviteMemberOkResponse: String = """{ "result": true }"""

  def getLoginInformationResponse(isLoginRequired: Boolean): String =
    s"""{
       | "isWebApi" : true,
       | "isLoginRequired" : $isLoginRequired,
       | "canMountFileSystem" : false,
       | "apiUrl" : "/",
       | "websocketUrl" : ":4041",
       | "localUser" : "username"
       |}""".stripMargin

  def loginResponse(username: String): String =
    s"""{
       |   "isLoginRequired" : false,
       |   "websocketUrl" : ":4041",
       |   "superPinExportRequired" : false,
       |   "reencryptionStatus" : {
       |      "pending" : false,
       |      "done" : 0,
       |      "total" : 0,
       |      "inProgress" : false,
       |      "isEncryption" : false
       |   },
       |   "initials" : "gr",
       |   "canMountFileSystem" : false,
       |   "isWebApi" : true,
       |   "address" : {
       |      "email" : "user@example.com",
       |      "id" : 1,
       |      "icon" : "self",
       |      "name" : "$username",
       |      "profile" : {
       |         "email" : "user@example.com",
       |         "phone" : "",
       |         "mobile" : ""
       |      },
       |      "initials" : "gr"
       |   },
       |   "localUser" : "$username",
       |   "superPinActivated" : false,
       |   "accessProtectionSettings" : true,
       |   "apiUrl" : "/",
       |   "username" : "username"
       |}""".stripMargin
}
