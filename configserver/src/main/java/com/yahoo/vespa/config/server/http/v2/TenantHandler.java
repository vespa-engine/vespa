// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.util.List;
import java.util.concurrent.Executor;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.application.ApplicationRepo;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.Tenants;

/**
 * Handler to create, get and delete a tenant.
 *
 * @author vegardh
 */
public class TenantHandler extends HttpHandler {

    private static final String TENANT_NAME_REGEXP = "[\\w-]+";
    private final Tenants tenants;

    public TenantHandler(Executor executor, AccessLog accessLog, Tenants tenants) {
        super(executor, accessLog);
        this.tenants = tenants;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        TenantName tenant = getAndValidateTenantFromRequest(request);
        try {
            tenants.createTenant(tenant);
        } catch (Exception e) {
            throw new InternalServerException(Exceptions.toMessageString(e));
        }
        return new TenantCreateResponse(tenant);
    }

    /**
     * Gets the tenant name from the request, throws if it exists already and validates its name
     *
     * @param request an {@link com.yahoo.container.jdisc.HttpRequest}
     * @return tenant name
     */
    private TenantName getAndValidateTenantFromRequest(HttpRequest request) {
        TenantName tenant = Utils.getTenantFromRequest(request);
        Utils.checkThatTenantDoesNotExist(tenants, tenant);
        validateTenantName(tenant);
        return tenant;
    }

    private void validateTenantName(TenantName tenant) {
        if (!tenant.value().matches(TENANT_NAME_REGEXP)) {
            throw new BadRequestException("Illegal tenant name: " + tenant);
        }
    }

    @Override
    protected HttpResponse handleGET(HttpRequest request) {
        TenantName tenant = getExistingTenant(request);
        return new TenantGetResponse(tenant);
    }

    @Override
    protected HttpResponse handleDELETE(HttpRequest request) {
        TenantName tenantName = getExistingTenant(request);
        Tenant tenant = Utils.checkThatTenantExists(tenants, tenantName);
        ApplicationRepo applicationRepo = tenant.getApplicationRepo();
        final List<ApplicationId> activeApplications = applicationRepo.listApplications();
        if (activeApplications.isEmpty()) {
            try {
                tenants.deleteTenant(tenantName);
            } catch (Exception e) {
                throw new InternalServerException(Exceptions.toMessageString(e));
            }
        } else {
            throw new BadRequestException("Cannot delete tenant '" + tenantName + "', as it has active applications: " +
                    activeApplications);
        }
        return new TenantDeleteResponse(tenantName);
    }

    private TenantName getExistingTenant(HttpRequest request) {
        TenantName tenant = Utils.getTenantFromRequest(request);
        Utils.checkThatTenantExists(tenants, tenant);
        return tenant;
    }
}
