package com.ubirch.provider;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.services.resource.RealmResourceProvider;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class SearchForUsersWithoutConfirmationMailResourceProvider implements RealmResourceProvider {

    private KeycloakSession session;

    public SearchForUsersWithoutConfirmationMailResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new SearchForUsersWithoutConfirmationMailResource(session);
    }

    @Override
    public void close() {

    }
}
