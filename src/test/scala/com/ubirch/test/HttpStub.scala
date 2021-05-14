package com.ubirch.test

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import sttp.model.{ HeaderNames, MediaType }

class HttpStub(wiremock: WireMockServer, val url: String, charset: String = "UTF-8") { self =>
  import HttpStub._

  private val ApplicationJson: String = MediaType.ApplicationJson.charset(charset).toString()

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

  def errorResponse(message: String = "some error message", statusCode: Int = 400): String =
    s"""{
       | "error" : 3,
       | "error_message" : "$message"
       | "result" : false,
       | "status_code" : $statusCode
       |}""".stripMargin
}
