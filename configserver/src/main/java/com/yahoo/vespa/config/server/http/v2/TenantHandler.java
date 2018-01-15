// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.util.List;
import com.google.inject.Inject;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.tenant.Tenants;

/**
 * Handler to create, get and delete a tenant, and listing of tenants.
 *
 * @author Vegard Havdal
 */
public class TenantHandler extends HttpHandler {

    private static final String TENANT_NAME_REGEXP = "[\\w-]+";
    private final Tenants tenants;

    @Inject
    public TenantHandler(HttpHandler.Context ctx, Tenants tenants) {
        super(ctx);
        this.tenants = tenants;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        TenantName tenantName = getAndValidateTenantFromRequest(request);
        try {
            tenants.addTenant(tenantName);
        } catch (Exception e) {
            throw new InternalServerException(Exceptions.toMessageString(e));
        }
        return new TenantCreateResponse(tenantName);
    }

    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        if (isGetTenantRequest(request)) {
            final TenantName tenantName = getTenantNameFromRequest(request);
            Utils.checkThatTenantExists(tenants, tenantName);
            return new TenantGetResponse(tenantName);
        } else if (isListTenantsRequest(request)) {
            return new ListTenantsResponse(tenants.getAllTenantNames());
        } else {
            throw new BadRequestException(request.getUri().toString());
        }
    }

    @Override
    protected HttpResponse handleDELETE(HttpRequest request) {
        final TenantName tenantName = getTenantNameFromRequest(request);
        Utils.checkThatTenantExists(tenants, tenantName);
        // TODO: Move logic to ApplicationRepository
        Tenant tenant = tenants.getTenant(tenantName);
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        final List<ApplicationId> activeApplications = applicationRepo.listApplications();
        if (activeApplications.isEmpty()) {
            try {
                tenants.deleteTenant(tenantName);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new InternalServerException(Exceptions.toMessageString(e));
            }
        } else {
            throw new BadRequestException("Cannot delete tenant '" + tenantName + "', as it has active applications: " +
                    activeApplications);
        }
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
        if (tenants.checkThatTenantExists(tenantName))
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
