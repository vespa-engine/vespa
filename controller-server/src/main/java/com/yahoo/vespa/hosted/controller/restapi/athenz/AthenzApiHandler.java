// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.athenz;

import com.google.inject.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;

import java.util.Map;
import java.util.logging.Logger;

import static com.yahoo.restapi.RestApi.route;

interface ResourceDefinition1 {
    HttpResponse root(RestApi.RequestContext ctx);
    Slime domainList(RestApi.RequestContext ctx);
}

interface ResourceDefinition2 {
    Slime properties(RestApi.RequestContext ctx);
    String signup(RestApi.RequestContext ctx);
}

class Resource1 implements ResourceDefinition1 {

    @Override
    public HttpResponse root(RestApi.RequestContext ctx) {
        return null;
    }

    @Override
    public Slime domainList(RestApi.RequestContext ctx) {
        return null;
    }
}

class Resource2 implements ResourceDefinition2 {

    @Override
    public Slime properties(RestApi.RequestContext ctx) {
        return null;
    }

    @Override
    public String signup(RestApi.RequestContext ctx) {
        return null;
    }
}

/**
 * This API proxies requests to an Athenz server.
 * 
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class AthenzApiHandler extends RestApiRequestHandler<AthenzApiHandler> implements ResourceDefinition1 {

    private final static Logger log = Logger.getLogger(AthenzApiHandler.class.getName());

    private final AthenzFacade athenz;
    private final AthenzDomain sandboxDomain;
    private final EntityService properties;

    @Inject
    public AthenzApiHandler(Context parentCtx, ResourceDefinition1 res1, ResourceDefinition2 res2) {
        super(parentCtx, createRestApi(res1, res2));
        this.athenz = athenz;
        this.sandboxDomain = new AthenzDomain(sandboxDomainIn(controller.system()));
        this.properties = controller.serviceRegistry().entityService();
    }

    private static RestApi createRestApi(ResourceDefinition1 res1, ResourceDefinition2 res2) {
        return RestApi.builder()
                .addRoute(route("/athenz/v1")
                        .get(res1::root))
                .addRoute(route("/athenz/v1/domains")
                        .get(res1::domainList))
                .addRoute(route("/athenz/v1/properties")
                        .get(res2::properties))
                .addRoute(route("/athenz/v1/user")
                        .post(res2::signup))
                .build();
    }

    @Override
    public HttpResponse root(RestApi.RequestContext ctx) {
        return new ResourceResponse(ctx.request(), "domains", "properties");
    }

    @Override
    public Slime properties(RestApi.RequestContext ctx) {
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        Cursor array = response.setArray("properties");
        for (Map.Entry<PropertyId, Property> entry : properties.listProperties().entrySet()) {
            Cursor propertyObject = array.addObject();
            propertyObject.setString("propertyid", entry.getKey().id());
            propertyObject.setString("property", entry.getValue().id());
        }
        return slime;
    }

    @Override
    public Slime domainList(RestApi.RequestContext ctx) {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("data");
        for (AthenzDomain athenzDomain : athenz.getDomainList(ctx.queryParameters().getString("prefix").orElse(null)))
            array.addString(athenzDomain.getName());

        return slime;
    }

    @Override
    public String signup(RestApi.RequestContext ctx) {
        AthenzUser user = athenzUser(ctx);
        athenz.addTenantAdmin(sandboxDomain, user);
        return "User '" + user.getName() + "' added to admin role of '" + sandboxDomain.getName() + "'";
    }

    private static AthenzUser athenzUser(RestApi.RequestContext ctx) {
        return ctx.userPrincipal()
                       .filter(AthenzPrincipal.class::isInstance)
                       .map(AthenzPrincipal.class::cast)
                       .map(AthenzPrincipal::getIdentity)
                       .filter(AthenzUser.class::isInstance)
                       .map(AthenzUser.class::cast)
                       .orElseThrow(() -> new IllegalArgumentException("No Athenz user principal on request"));
    }

    static String sandboxDomainIn(SystemName system) {
        switch (system) {
            case main: return "vespa.vespa.tenants.sandbox";
            case cd:   return "vespa.vespa.cd.tenants.sandbox";
            default:   throw new IllegalArgumentException("No sandbox domain in system '" + system + "'");
        }
    }

}
