// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.os;

import com.yahoo.component.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.CloudName;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.logging.Level;

/**
 * This implements the /os/v1 API which provides operators with information about, and scheduling of OS upgrades for
 * nodes in the system.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class OsApiHandler extends LoggingRequestHandler {

    private final Controller controller;

    public OsApiHandler(Context ctx, Controller controller) {
        super(ctx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return get(request);
                case PATCH: return patch(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse patch(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/os/v1/")) return new SlimeJsonResponse(setOsVersion(request));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/os/v1/")) return new SlimeJsonResponse(osVersions());
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private Slime setOsVersion(HttpRequest request) {
        Slime requestData = toSlime(request.getData());
        Inspector root = requestData.get();
        Inspector versionField = root.field("version");
        Inspector cloudField = root.field("cloud");
        if (!versionField.valid() || !cloudField.valid()) {
            throw new IllegalArgumentException("Fields 'version' and 'cloud' are required");
        }

        CloudName cloud = CloudName.from(cloudField.asString());
        if (controller.zoneRegistry().zones().all().ids().stream().noneMatch(zone -> cloud.equals(zone.cloud()))) {
            throw new IllegalArgumentException("Cloud '" + cloud.value() + "' does not exist in this system");
        }

        Version target;
        try {
            target = Version.fromString(versionField.asString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid version '" + versionField.asString() + "'", e);
        }

        controller.upgradeOsIn(cloud, target);
        Slime response = new Slime();
        Cursor cursor = response.setObject();
        cursor.setString("message", "Set target OS version for cloud '" + cloud.value() + "' to " +
                                    target.toFullString());
        return response;
    }

    private Slime osVersions() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Set<OsVersion> osVersions = controller.osVersions();

        Cursor versions = root.setArray("versions");
        controller.osVersionStatus().versions().forEach((osVersion, nodes) -> {
            Cursor currentVersionObject = versions.addObject();
            currentVersionObject.setString("version", osVersion.version().toFullString());
            currentVersionObject.setBool("targetVersion", osVersions.contains(osVersion));
            currentVersionObject.setString("cloud", osVersion.cloud().value());
            Cursor nodesArray = currentVersionObject.setArray("nodes");
            nodes.forEach(node -> {
                Cursor nodeObject = nodesArray.addObject();
                nodeObject.setString("hostname", node.hostname().value());
                nodeObject.setString("environment", node.environment().value());
                nodeObject.setString("region", node.region().value());
            });
        });

        return slime;
    }

    private static Slime toSlime(InputStream json) {
        try {
            return SlimeUtils.jsonToSlime(IOUtils.readBytes(json, 1000 * 1000));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
