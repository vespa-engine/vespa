// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;

import java.util.logging.Level;

/**
 * Handler implementing the Athenz Identity Provider API (Copper Argos).
 *
 * @author bjorncs
 */
public class IdentityProviderRequestHandler extends RestApiRequestHandler<IdentityProviderRequestHandler> {

    private final IdentityDocumentGenerator documentGenerator;
    private final InstanceValidator instanceValidator;

    @Inject
    public IdentityProviderRequestHandler(ThreadedHttpRequestHandler.Context context,
                                          IdentityDocumentGenerator documentGenerator,
                                          InstanceValidator instanceValidator) {
        super(context, IdentityProviderRequestHandler::createRestApi);
        this.documentGenerator = documentGenerator;
        this.instanceValidator = instanceValidator;
    }

    private static RestApi createRestApi(IdentityProviderRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/athenz/v1/provider/identity-document/node/{host}")
                        .get(self::getNodeIdentityDocument))
                .addRoute(RestApi.route("/athenz/v1/provider/identity-document/tenant/{host}")
                        .get(self::getTenantIdentityDocument))
                .addRoute(RestApi.route("/athenz/v1/provider/instance")
                        .post(InstanceConfirmation.class, self::confirmInstance))
                .addRoute(RestApi.route("/athenz/v1/provider/refresh")
                        .post(InstanceConfirmation.class, self::confirmInstanceRefresh))
                .registerJacksonRequestEntity(InstanceConfirmation.class)
                .registerJacksonResponseEntity(InstanceConfirmation.class)
                .registerJacksonResponseEntity(SignedIdentityDocumentEntity.class)
                // Overriding object mapper to change serialization of timestamps
                .setObjectMapper(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .registerModule(new Jdk8Module())
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true))
                .build();
    }

    private SignedIdentityDocumentEntity getNodeIdentityDocument(RestApi.RequestContext context) {
        String host = context.pathParameters().getString("host").orElse(null);
        return getIdentityDocument(host, IdentityType.NODE);
    }

    private SignedIdentityDocumentEntity getTenantIdentityDocument(RestApi.RequestContext context) {
        String host = context.pathParameters().getString("host").orElse(null);
        return getIdentityDocument(host, IdentityType.TENANT);
    }

    private InstanceConfirmation confirmInstance(RestApi.RequestContext context, InstanceConfirmation instanceConfirmation) {
        log.log(Level.FINE, () -> instanceConfirmation.toString());
        if (!instanceValidator.isValidInstance(instanceConfirmation)) {
            log.log(Level.SEVERE, "Invalid instance: " + instanceConfirmation);
            throw new RestApiException.Forbidden("Instance is invalid");
        }
        return instanceConfirmation;
    }

    private InstanceConfirmation confirmInstanceRefresh(RestApi.RequestContext context, InstanceConfirmation instanceConfirmation) {
        log.log(Level.FINE, () -> instanceConfirmation.toString());
        if (!instanceValidator.isValidRefresh(instanceConfirmation)) {
            log.log(Level.SEVERE, "Invalid instance refresh: " + instanceConfirmation);
            throw new RestApiException.Forbidden("Instance is invalid");
        }
        return instanceConfirmation;
    }

    private SignedIdentityDocumentEntity getIdentityDocument(String hostname, IdentityType identityType) {
        if (hostname == null) {
            throw new RestApiException.BadRequest("The 'hostname' query parameter is missing");
        }
        try {
            return EntityBindingsMapper.toSignedIdentityDocumentEntity(documentGenerator.generateSignedIdentityDocument(hostname, identityType));
        } catch (Exception e) {
            String message = String.format("Unable to generate identity document for '%s': %s", hostname, e.getMessage());
            log.log(Level.SEVERE, message, e);
            throw new RestApiException.InternalServerError(message, e);
        }
    }
}
