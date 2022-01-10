// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.athenz;

import com.yahoo.component.annotation.Inject;
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

/**
 * This API proxies requests to an Athenz server.
 * 
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class AthenzApiHandler extends RestApiRequestHandler<AthenzApiHandler> {

    private final static Logger log = Logger.getLogger(AthenzApiHandler.class.getName());

    private final AthenzFacade athenz;
    private final AthenzDomain sandboxDomain;
    private final EntityService properties;

    @Inject
    public AthenzApiHandler(Context parentCtx, AthenzFacade athenz, Controller controller) {
        super(parentCtx, AthenzApiHandler::createRestApi);
        this.athenz = athenz;
        this.sandboxDomain = new AthenzDomain(sandboxDomainIn(controller.system()));
        this.properties = controller.serviceRegistry().entityService();
    }

    private static RestApi createRestApi(AthenzApiHandler self) {
        return RestApi.builder()
                .addRoute(route("/athenz/v1")
                        .get(self::root))
                .addRoute(route("/athenz/v1/domains")
                        .get(self::domainList))
                .addRoute(route("/athenz/v1/properties")
                        .get(self::properties))
                .addRoute(route("/athenz/v1/user")
                        .post(self::signup))
                .build();
    }

    private HttpResponse root(RestApi.RequestContext ctx) {
        return new ResourceResponse(ctx.request(), "domains", "properties");
    }

    private Slime properties(RestApi.RequestContext ctx) {
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

    private Slime domainList(RestApi.RequestContext ctx) {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("data");
        for (AthenzDomain athenzDomain : athenz.getDomainList(ctx.queryParameters().getString("prefix").orElse(null)))
            array.addString(athenzDomain.getName());

        return slime;
    }

    private String signup(RestApi.RequestContext ctx) {
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
