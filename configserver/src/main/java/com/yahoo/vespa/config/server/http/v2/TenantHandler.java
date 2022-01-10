// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.v2.response.ListTenantsResponse;
import com.yahoo.vespa.config.server.http.v2.response.TenantCreateResponse;
import com.yahoo.vespa.config.server.http.v2.response.TenantDeleteResponse;
import com.yahoo.vespa.config.server.http.v2.response.TenantGetResponse;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.yolean.Exceptions;

/**
 * Handler to create, get and delete a tenant, and listing of tenants.
 *
 * @author jonmv
 */
public class TenantHandler extends RestApiRequestHandler<TenantHandler> {

    private static final String TENANT_NAME_REGEXP = "[\\w-]+";
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    @Inject
    public TenantHandler(Context ctx, ApplicationRepository applicationRepository) {
        super(ctx, TenantHandler::defineApi);
        this.tenantRepository = applicationRepository.tenantRepository();
        this.applicationRepository = applicationRepository;
    }

    private RestApi defineApi() {
        return RestApi.builder()
                      .addRoute(RestApi.route("/application/v2/tenant")
                                       .get(this::getTenants))
                      .addRoute(RestApi.route("/application/v2/tenant/{tenant}")
                                       .get(this::getTenant)
                                       .put(this::putTenant)
                                       .delete(this::deleteTenant))
                      .addExceptionMapper(IllegalArgumentException.class, (c, e) -> ErrorResponse.badRequest(Exceptions.toMessageString(e)))
                      .addExceptionMapper(RuntimeException.class, (c, e) -> ErrorResponse.internalServerError(Exceptions.toMessageString(e)))
                      .build();
    }

    private HttpResponse getTenants(RestApi.RequestContext context) {
        return new ListTenantsResponse(ImmutableSet.copyOf(tenantRepository.getAllTenantNames()));
    }

    private HttpResponse getTenant(RestApi.RequestContext context) {
        TenantName name = TenantName.from(context.pathParameters().getStringOrThrow("tenant"));
        if ( ! tenantRepository.checkThatTenantExists(name))
            throw new RestApiException.NotFound("Tenant '" + name + "' was not found.");

        return new TenantGetResponse(name);
    }

    private HttpResponse putTenant(RestApi.RequestContext context) {
        TenantName name = TenantName.from(context.pathParameters().getStringOrThrow("tenant"));
        if (tenantRepository.checkThatTenantExists(name))
            throw new RestApiException.BadRequest("There already exists a tenant '" + name + "'");
        if ( ! name.value().matches(TENANT_NAME_REGEXP))
            throw new RestApiException.BadRequest("Illegal tenant name: " + name);

        tenantRepository.addTenant(name);
        return new TenantCreateResponse(name);
    }

    private HttpResponse deleteTenant(RestApi.RequestContext context) {
        TenantName name = TenantName.from(context.pathParameters().getStringOrThrow("tenant"));
        if ( ! tenantRepository.checkThatTenantExists(name))
            throw new RestApiException.NotFound("Tenant '" + name + "' was not found.");

        applicationRepository.deleteTenant(name);
        return new TenantDeleteResponse(name);
    }

}
