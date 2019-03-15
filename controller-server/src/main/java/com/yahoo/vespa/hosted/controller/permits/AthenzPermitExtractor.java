package com.yahoo.vespa.hosted.controller.permits;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;

import java.util.Objects;
import java.util.Optional;

import static com.yahoo.io.IOUtils.readBytes;
import static com.yahoo.vespa.config.SlimeUtils.jsonToSlime;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Extracts permits for Athenz or user tenants from HTTP requests.
 */
public class AthenzPermitExtractor implements PermitExtractor {

    private final Controller controller;

    @Inject
    public AthenzPermitExtractor(Controller controller) {
        this.controller = Objects.requireNonNull(controller);
    }

    @Override
    public TenantPermit getTenantPermit(TenantName tenant, HttpRequest request) {
        Inspector root = jsonToSlime(uncheck(() -> readBytes(request.getData(), 1 << 20))).get();
        return new AthenzTenantPermit(tenant,
                                      request.getJDiscRequest().getUserPrincipal(),
                                      optional("athensDomain", root).map(AthenzDomain::new),
                                      optional("property", root).map(Property::new),
                                      optional("propertyId", root).map(PropertyId::new),
                                      requireOktaAccessToken(request));
    }

    @Override
    public ApplicationPermit getApplicationPermit(ApplicationId application, HttpRequest request) {
        return new AthenzApplicationPermit(application,
                                           ((AthenzTenant) controller.tenants().require(application.tenant())).domain(),
                                           requireOktaAccessToken(request));
    }

    private static OktaAccessToken requireOktaAccessToken(HttpRequest request) {
        return Optional.ofNullable(request.getJDiscRequest().context().get("okta.access-token"))
                       .map(attribute -> new OktaAccessToken((String) attribute))
                       .orElseThrow(() -> new IllegalArgumentException("No Okta Access Token provided"));
    }

    private static String required(String fieldName, Inspector object) {
        return optional(fieldName, object) .orElseThrow(() -> new IllegalArgumentException("Missing required field '" + fieldName + "'."));
    }

    private static Optional<String> optional(String fieldName, Inspector object) {
        return object.field(fieldName).valid() ? Optional.of(object.field(fieldName).asString()) : Optional.empty();
    }

}
