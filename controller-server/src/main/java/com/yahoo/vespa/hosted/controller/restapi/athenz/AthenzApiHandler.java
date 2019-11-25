// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.athenz;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.yolean.Exceptions;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * This API proxies requests to an Athenz server.
 * 
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class AthenzApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(AthenzApiHandler.class.getName());

    private final AthenzFacade athenz;
    private final AthenzDomain sandboxDomain;
    private final EntityService properties;
    private final TenantController tenants;
    private final ApplicationController applications;

    public AthenzApiHandler(Context parentCtx, AthenzFacade athenz, Controller controller) {
        super(parentCtx);
        this.athenz = athenz;
        this.sandboxDomain = new AthenzDomain(sandboxDomainIn(controller.system()));
        this.properties = controller.serviceRegistry().entityService();
        this.tenants = controller.tenants();
        this.applications = controller.applications();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Method method = request.getMethod();
        try {
            switch (method) {
                case GET: return get(request);
                case POST: return post(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + method + "' is unsupported");
            }
        }
        catch (IllegalArgumentException|IllegalStateException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/athenz/v1")) return root(request);
        if (path.matches("/athenz/v1/domains")) return domainList(request);
        if (path.matches("/athenz/v1/properties")) return properties();
        if (path.matches("/athenz/v1/user")) return accessibleInstances(request);

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/athenz/v1/user")) return signup(request);
        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse root(HttpRequest request) {
        return new ResourceResponse(request, "domains", "properties");
    }


    private HttpResponse properties() {
        Slime slime = new Slime();
        Cursor response = slime.setObject();
        Cursor array = response.setArray("properties");
        for (Map.Entry<PropertyId, Property> entry : properties.listProperties().entrySet()) {
            Cursor propertyObject = array.addObject();
            propertyObject.setString("propertyid", entry.getKey().id());
            propertyObject.setString("property", entry.getValue().id());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse domainList(HttpRequest request) {
        Slime slime = new Slime();
        Cursor array = slime.setObject().setArray("data");
        for (AthenzDomain athenzDomain : athenz.getDomainList(request.getProperty("prefix")))
            array.addString(athenzDomain.getName());

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse signup(HttpRequest request) {
        AthenzUser user = athenzUser(request);
        athenz.addTenantAdmin(sandboxDomain, user);
        return new MessageResponse("User '" + user.getName() + "' added to admin role of '" + sandboxDomain.getName() + "'");
    }

    private HttpResponse accessibleInstances(HttpRequest request) {
        Slime slime = new Slime();
        Cursor tenantsObject = slime.setObject().setObject("tenants");
        var instances = accessibleInstances(athenzUser(request));
        instances.keySet().stream().sorted().forEach(tenant -> {
            Cursor tenantObject = tenantsObject.setObject(tenant.value());

            List.of(RoleDefinition.administrator, RoleDefinition.developer).stream().map(Enum::name)
                .forEach(tenantObject.setArray("roles")::addString); // Make roles comply with console.

            Cursor applicationsObject = tenantObject.setObject("applications");
            instances.get(tenant).keySet().stream().sorted()
                     .forEach(application -> instances.get(tenant).get(application).stream().sorted().map(InstanceName::value)
                                                      .forEach(applicationsObject.setObject(application.value()).setArray("instances")::addString));
        });
        return new SlimeJsonResponse(slime);
    }

    /**
     * Returns the list of accessible instances for the given user, under all existing Athenz tenants.
     *
     * For regular tenants, all applications are included, with all existing instances.
     * For the sandbox tenant, only applications where the user has a dev instance are included, with that instance.
     */
    private Map<TenantName, Map<ApplicationName, Set<InstanceName>>> accessibleInstances(AthenzUser user) {
        List<AthenzDomain> userDomains = athenz.userDomains(user);
        return tenants.asList()
                      .stream()
                      .filter(AthenzTenant.class::isInstance)
                      .map(AthenzTenant.class::cast)
                      .filter(tenant -> userDomains.contains(tenant.domain()))
                      .collect(toUnmodifiableMap(tenant -> tenant.name(),
                                                 tenant -> accessibleInstances(tenant, user)));
    }

    private Map<ApplicationName, Set<InstanceName>> accessibleInstances(AthenzTenant tenant, AthenzUser user) {
        InstanceName userInstance = InstanceName.from(user.getName());
        return applications.asList(tenant.name()).stream()
                           .filter(application ->    ! sandboxDomain.equals(tenant.domain())
                                                  ||   application.instances().containsKey(userInstance))
                           .collect(toUnmodifiableMap(application -> application.id().application(),
                                                      application -> application.instances().values().stream()
                                                                                .filter(instance ->    ! sandboxDomain.equals(tenant.domain())
                                                                                                    ||   userInstance.equals(instance.name()))
                                                                                .map(Instance::name)
                                                                                .collect(toUnmodifiableSet())));
    }

    private static AthenzUser athenzUser(HttpRequest request) {
        return Optional.ofNullable(request.getJDiscRequest().getUserPrincipal()).filter(AthenzPrincipal.class::isInstance)
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
