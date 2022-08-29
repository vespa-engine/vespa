// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.configserver;

import ai.vespa.http.HttpURL;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.proxy.ConfigServerRestExecutor;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.vespa.http.HttpURL.Path.parse;

/**
 * REST API for proxying operator APIs to config servers in a given zone.
 *
 * @author freva
 */
@SuppressWarnings("unused")
public class ConfigServerApiHandler extends AuditLoggingRequestHandler {

    private static final URI CONTROLLER_URI = URI.create("https://localhost:4443/");
    private static final List<HttpURL.Path> WHITELISTED_APIS = List.of(parse("/flags/v1/"),
                                                                       parse("/nodes/v2/"),
                                                                       parse("/orchestrator/v1/"));

    private final ZoneRegistry zoneRegistry;
    private final ConfigServerRestExecutor proxy;
    private final ZoneId controllerZone;

    public ConfigServerApiHandler(Context parentCtx, ServiceRegistry serviceRegistry,
                                  ConfigServerRestExecutor proxy, Controller controller) {
        super(parentCtx, controller.auditLogger());
        this.zoneRegistry = serviceRegistry.zoneRegistry();
        this.controllerZone = zoneRegistry.systemZone().getVirtualId();
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
            return ErrorResponses.logThrowing(request, log, e);
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
        if ( ! zoneRegistry.hasZone(zoneId) && ! controllerZone.equals(zoneId)) {
            throw new IllegalArgumentException("No such zone: " + zoneId.value());
        }

        if (path.getRest().length() < 2 || ! WHITELISTED_APIS.contains(path.getRest().head(2).withTrailingSlash())) {
            return ErrorResponse.forbidden("Cannot access " + path.getRest() +
                    " through /configserver/v1, following APIs are permitted: " + WHITELISTED_APIS.stream()
                                                                                                  .map(p -> "/" + String.join("/", p.segments()) + "/")
                                                                                                  .collect(Collectors.joining(", ")));
        }

        return proxy.handle(ProxyRequest.tryOne(getEndpoint(zoneId), path.getRest(), request));
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        ZoneList zoneList = zoneRegistry.zones().reachable();

        Cursor zones = root.setArray("zones");
        Stream.concat(Stream.of(controllerZone), zoneRegistry.zones().reachable().ids().stream())
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
        return controllerZone.equals(zoneId) ? CONTROLLER_URI : zoneRegistry.getConfigServerVipUri(zoneId);
    }

}
