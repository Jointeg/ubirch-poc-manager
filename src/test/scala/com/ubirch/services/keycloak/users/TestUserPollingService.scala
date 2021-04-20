package com.ubirch.services.keycloak.users
import com.ubirch.models.keycloak.user.KeycloakUser
import monix.reactive.Observable

import javax.inject.Singleton

@Singleton
class TestUserPollingService() extends UserPollingService {

  // Right now we do nothing in Unit Tests. We can test this functionality in E2E tests
  override def via[T](operation: Either[Exception, List[KeycloakUser]] => Observable[T]): Observable[T] =
    Observable.empty
}
