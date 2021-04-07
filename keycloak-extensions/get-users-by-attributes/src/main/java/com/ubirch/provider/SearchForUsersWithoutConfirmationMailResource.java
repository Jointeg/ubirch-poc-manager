package com.ubirch.provider;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class SearchForUsersWithoutConfirmationMailResource {
    private final KeycloakSession session;
    private final AuthenticationManager.AuthResult authResult;

    public SearchForUsersWithoutConfirmationMailResource(KeycloakSession session) {
        this.session = session;
        this.authResult = new AppAuthManager().authenticateBearerToken(session);
    }

    @GET
    @Path("users-without-confirmation-mail")
    @Produces({MediaType.APPLICATION_JSON})
    public List<UserRepresentation> getUsersWithoutConfirmationMail() {
        checkRealmAdmin();
        return session
                .users()
                .searchForUserByUserAttribute("confirmation_mail_sent", "false", session.getContext().getRealm())
                .stream()
                .filter(UserModel::isEmailVerified)
                .map(userModel -> ModelToRepresentation.toRepresentation(session, session.getContext().getRealm(), userModel))
                .collect(toList());
    }

    private void checkRealmAdmin() {
        if (authResult == null) {
            throw new NotAuthorizedException("Bearer");
        } else if (authResult.getToken().getRealmAccess() == null || !authResult.getToken().getRealmAccess().isUserInRole("admin")) {
            throw new ForbiddenException("Does not have realm admin role");
        }
    }
}
