// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.Utils;

import java.util.Collection;
import java.util.List;

/**
 * Handler for listing currently active applications for a tenant.
 *
 * @author lulf
 * @since 5.1
 */
public class ListApplicationsHandler extends HttpHandler {
    private final TenantRepository tenantRepository;
    private final Zone zone;

    @Inject
    public ListApplicationsHandler(HttpHandler.Context ctx,
                                   TenantRepository tenantRepository, Zone zone) {
        super(ctx);
        this.tenantRepository = tenantRepository;
        this.zone = zone;
    }

    @Override
    public HttpResponse handleGET(HttpRequest request) {
        TenantName tenantName = Utils.getTenantNameFromApplicationsRequest(request);
        final String urlBase = Utils.getUrlBase(request, "/application/v2/tenant/" + tenantName + "/application/");

        List<ApplicationId> applicationIds = listApplicationIds(tenantName);
        Collection<String> applicationUrls = Collections2.transform(applicationIds, new Function<ApplicationId, String>() {
            @Override
            public String apply(ApplicationId id) {
                return createUrlStringFromId(urlBase, id, zone);
            }
        });
        return new ListApplicationsResponse(Response.Status.OK, applicationUrls);
    }

    private List<ApplicationId> listApplicationIds(TenantName tenantName) {
        Utils.checkThatTenantExists(tenantRepository, tenantName);
        Tenant tenant = tenantRepository.getTenant(tenantName);
        TenantApplications applicationRepo = tenant.getApplicationRepo();
        return applicationRepo.listApplications();
    }

    private static String createUrlStringFromId(String urlBase, ApplicationId id, Zone zone) {
        StringBuilder sb = new StringBuilder();
        sb.append(urlBase).append(id.application().value());
        sb.append("/environment/").append(zone.environment().value());
        sb.append("/region/").append(zone.region().value());
        sb.append("/instance/").append(id.instance().value());
        return sb.toString();
    }
}
