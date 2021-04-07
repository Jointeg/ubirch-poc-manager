package com.ubirch.provider;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class SearchForUsersWithoutConfirmationMailProviderFactory implements RealmResourceProviderFactory {

    @Override
    public SearchForUsersWithoutConfirmationMailResourceProvider create(KeycloakSession session) {
        return new SearchForUsersWithoutConfirmationMailResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "user-search";
    }
}
