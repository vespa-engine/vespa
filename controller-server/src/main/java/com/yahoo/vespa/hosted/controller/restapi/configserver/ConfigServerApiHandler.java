// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.configserver;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.proxy.ConfigServerRestExecutor;
import com.yahoo.vespa.hosted.controller.proxy.ProxyException;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.yolean.Exceptions;

import javax.net.ssl.HostnameVerifier;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * REST API for proxying operator APIs to config servers in a given zone.
 *
 * @author freva
 */
@SuppressWarnings("unused")
public class ConfigServerApiHandler extends AuditLoggingRequestHandler {

    private static final ZoneId CONTROLLER_ZONE = ZoneId.from("prod", "controller");
    private static final List<String> WHITELISTED_APIS = List.of("/flags/v1/", "/nodes/v2/", "/orchestrator/v1/");

    private final ZoneRegistry zoneRegistry;
    private final ConfigServerRestExecutor proxy;

    public ConfigServerApiHandler(Context parentCtx, ZoneRegistry zoneRegistry,
                                  ConfigServerRestExecutor proxy, Controller controller) {
        super(parentCtx, controller.auditLogger());
        this.zoneRegistry = zoneRegistry;
        this.proxy = proxy;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET:
                    return get(request);
                case POST:
                case PUT:
                case DELETE:
                case PATCH:
                    return proxy(request);
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "', "
                                   + Exceptions.toMessageString(e));
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/configserver/v1")) {
            return root(request);
        }
        return proxy(request);
    }

    private HttpResponse proxy(HttpRequest request) {
        Path path = new Path(request.getUri());
        if ( ! path.matches("/configserver/v1/{environment}/{region}/{*}")) {
            return ErrorResponse.notFoundError("Nothing at " + path);
        }

        ZoneId zoneId = ZoneId.from(path.get("environment"), path.get("region"));
        if (! zoneRegistry.hasZone(zoneId) && ! CONTROLLER_ZONE.equals(zoneId)) {
            throw new IllegalArgumentException("No such zone: " + zoneId.value());
        }

        String cfgPath = "/" + path.getRest();
        if (WHITELISTED_APIS.stream().noneMatch(cfgPath::startsWith)) {
            return ErrorResponse.forbidden("Cannot access '" + cfgPath +
                    "' through /configserver/v1, following APIs are permitted: " + String.join(", ", WHITELISTED_APIS));
        }

        try {
            return proxy.handle(new ProxyRequest(
                    request, List.of(getEndpoint(zoneId)), createHostnameVerifier(zoneId), cfgPath));
        } catch (ProxyException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        ZoneList zoneList = zoneRegistry.zones().reachable();

        Cursor zones = root.setArray("zones");
        Stream.concat(Stream.of(CONTROLLER_ZONE), zoneRegistry.zones().reachable().ids().stream())
                .forEach(zone -> {
            Cursor object = zones.addObject();
            object.setString("environment", zone.environment().value());
            object.setString("region", zone.region().value());
            object.setString("uri", request.getUri().resolve(
                    "/configserver/v1/" + zone.environment().value() + "/" + zone.region().value()).toString());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse notFound(Path path) {
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private URI getEndpoint(ZoneId zoneId) {
        return CONTROLLER_ZONE.equals(zoneId) ? zoneRegistry.apiUrl() : zoneRegistry.getConfigServerVipUri(zoneId);
    }

    private HostnameVerifier createHostnameVerifier(ZoneId zoneId) {
        if (CONTROLLER_ZONE.equals(zoneId)) return null;
        return new AthenzIdentityVerifier(Set.of(zoneRegistry.getConfigServerHttpsIdentity(zoneId)));
    }
}
