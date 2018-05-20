// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.Utils;

/**
 * Handler to create, get and delete a tenant, and listing of tenants.
 *
 * @author Vegard Havdal
 */
public class TenantHandler extends HttpHandler {

    private static final String TENANT_NAME_REGEXP = "[\\w-]+";
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    @Inject
    public TenantHandler(Context ctx, TenantRepository tenantRepository, ApplicationRepository applicationRepository) {
        super(ctx);
        this.tenantRepository = tenantRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        TenantName tenantName = getAndValidateTenantFromRequest(request);
        try {
            tenantRepository.addTenant(tenantName);
        } catch (Exception e) {
            throw new InternalServerException(Exceptions.toMessageString(e));
        }
        return new TenantCreateResponse(tenantName);
    }

    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        if (isGetTenantRequest(request)) {
            final TenantName tenantName = getTenantNameFromRequest(request);
            Utils.checkThatTenantExists(tenantRepository, tenantName);
            return new TenantGetResponse(tenantName);
        } else if (isListTenantsRequest(request)) {
            return new ListTenantsResponse(tenantRepository.getAllTenantNames());
        } else {
            throw new BadRequestException(request.getUri().toString());
        }
    }

    @Override
    protected HttpResponse handleDELETE(HttpRequest request) {
        final TenantName tenantName = getTenantNameFromRequest(request);
        Utils.checkThatTenantExists(tenantRepository, tenantName);
        applicationRepository.deleteTenant(tenantName);
        return new TenantDeleteResponse(tenantName);
    }

    /**
     * Gets the tenant name from the request, throws if it exists already and validates its name
     *
     * @param request an {@link com.yahoo.container.jdisc.HttpRequest}
     * @return tenant name
     */
    private TenantName getAndValidateTenantFromRequest(HttpRequest request) {
        final TenantName tenantName = getTenantNameFromRequest(request);
        checkThatTenantDoesNotExist(tenantName);
        validateTenantName(tenantName);
        return tenantName;
    }

    private void validateTenantName(TenantName tenant) {
        if (!tenant.value().matches(TENANT_NAME_REGEXP)) {
            throw new BadRequestException("Illegal tenant name: " + tenant);
        }
    }

    private void checkThatTenantDoesNotExist(TenantName tenantName) {
        if (tenantRepository.checkThatTenantExists(tenantName))
            throw new BadRequestException("There already exists a tenant '" + tenantName + "'");
    }

    private static BindingMatch<?> getBindingMatch(HttpRequest request) {
        return HttpConfigRequests.getBindingMatch(request,
                "http://*/application/v2/tenant/",
                "http://*/application/v2/tenant/*");
    }

    private static boolean isGetTenantRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 3;
    }

    private static boolean isListTenantsRequest(HttpRequest request) {
        return getBindingMatch(request).groupCount() == 2 &&
                request.getUri().getPath().endsWith("/tenant/");
    }

    private static TenantName getTenantNameFromRequest(HttpRequest request) {
        BindingMatch<?> bm = getBindingMatch(request);
        return TenantName.from(bm.group(2));
    }

}
