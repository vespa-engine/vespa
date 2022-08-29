// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.horizon;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.horizon.HorizonClient;
import com.yahoo.vespa.hosted.controller.api.integration.horizon.HorizonResponse;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proxies metrics requests from Horizon UI
 *
 * @author valerijf
 */
public class HorizonApiHandler extends ThreadedHttpRequestHandler {

    private final SystemName systemName;
    private final HorizonClient client;
    private final BooleanFlag enabledHorizonDashboard;

    private static final EnumSet<RoleDefinition> operatorRoleDefinitions =
            EnumSet.of(RoleDefinition.hostedOperator, RoleDefinition.hostedSupporter);

    @Inject
    public HorizonApiHandler(ThreadedHttpRequestHandler.Context parentCtx, Controller controller, FlagSource flagSource) {
        super(parentCtx);
        this.systemName = controller.system();
        this.client = controller.serviceRegistry().horizonClient();
        this.enabledHorizonDashboard = Flags.ENABLED_HORIZON_DASHBOARD.bindTo(flagSource);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        var roles = getRoles(request);
        var operator = roles.stream().map(Role::definition).anyMatch(operatorRoleDefinitions::contains);
        var authorizedTenants = getAuthorizedTenants(roles);

        if (!operator && authorizedTenants.isEmpty())
            return ErrorResponse.forbidden("No tenant with enabled metrics view");

        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
                case POST -> post(request, authorizedTenants, operator);
                case PUT -> put(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/horizon/v1/config/dashboard/topFolders")) return new JsonInputStreamResponse(client.getTopFolders());
        if (path.matches("/horizon/v1/config/dashboard/file/{id}")) return getDashboard(path.get("id"));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(HttpRequest request, Set<TenantName> authorizedTenants, boolean operator) {
        Path path = new Path(request.getUri());
        if (path.matches("/horizon/v1/tsdb/api/query/graph")) return metricQuery(request, authorizedTenants, operator);
        if (path.matches("/horizon/v1/meta/search/timeseries")) return metaQuery(request, authorizedTenants, operator);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse put(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/horizon/v1/config/user")) return new JsonInputStreamResponse(client.getUser());
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse metricQuery(HttpRequest request, Set<TenantName> authorizedTenants, boolean operator) {
        try {
            byte[] data = TsdbQueryRewriter.rewrite(request.getData().readAllBytes(), authorizedTenants, operator, systemName);
            return new JsonInputStreamResponse(client.getMetrics(data));
        } catch (TsdbQueryRewriter.UnauthorizedException e) {
            return ErrorResponse.forbidden("Access denied");
        } catch (IOException e) {
            return ErrorResponse.badRequest("Failed to parse request body: " + e.getMessage());
        }
    }

    private HttpResponse metaQuery(HttpRequest request, Set<TenantName> authorizedTenants, boolean operator) {
        try {
            byte[] data = TsdbQueryRewriter.rewrite(request.getData().readAllBytes(), authorizedTenants, operator, systemName);
            return new JsonInputStreamResponse(client.getMetaData(data));
        } catch (TsdbQueryRewriter.UnauthorizedException e) {
            return ErrorResponse.forbidden("Access denied");
        } catch (IOException e) {
            return ErrorResponse.badRequest("Failed to parse request body: " + e.getMessage());
        }
    }

    private HttpResponse getDashboard(String id) {
        try {
            int dashboardId = Integer.parseInt(id);
            return new JsonInputStreamResponse(client.getDashboard(dashboardId));
        } catch (NumberFormatException e) {
            return ErrorResponse.badRequest("Dashboard ID must be integer, was " + id);
        }
    }

    private static Set<Role> getRoles(HttpRequest request) {
        return Optional.ofNullable(request.getJDiscRequest().context().get(SecurityContext.ATTRIBUTE_NAME))
                .filter(SecurityContext.class::isInstance)
                .map(SecurityContext.class::cast)
                .map(SecurityContext::roles)
                .orElseThrow(() -> new IllegalArgumentException("Attribute '" + SecurityContext.ATTRIBUTE_NAME + "' was not set on request"));
    }

    private Set<TenantName> getAuthorizedTenants(Set<Role> roles) {
        return roles.stream()
                .filter(TenantRole.class::isInstance)
                .map(role -> ((TenantRole) role).tenant())
                .filter(tenant -> enabledHorizonDashboard.with(FetchVector.Dimension.TENANT_ID, tenant.value()).value())
                .collect(Collectors.toSet());
    }

    private static class JsonInputStreamResponse extends HttpResponse {

        private final HorizonResponse response;

        public JsonInputStreamResponse(HorizonResponse response) {
            super(response.code());
            this.response = response;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            try (InputStream inputStream = response.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        }
    }
}
