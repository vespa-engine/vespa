// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneList;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.MessageResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Level;

/**
 * This implements the /os/v1 API which provides operators with information about, and scheduling of OS upgrades for
 * nodes in the system.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class OsApiHandler extends AuditLoggingRequestHandler {

    private final Controller controller;

    public OsApiHandler(Context ctx, Controller controller) {
        super(ctx, controller.auditLogger());
        this.controller = controller;
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return get(request);
                case POST: return post(request);
                case DELETE: return delete(request);
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
        Path path = new Path(request.getUri());
        if (path.matches("/os/v1/")) return new SlimeJsonResponse(setOsVersion(request));
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/os/v1/")) return new SlimeJsonResponse(osVersions());
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/os/v1/firmware/")) return requestFirmwareCheckResponse(path);
        if (path.matches("/os/v1/firmware/{environment}/")) return requestFirmwareCheckResponse(path);
        if (path.matches("/os/v1/firmware/{environment}/{region}/")) return requestFirmwareCheckResponse(path);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse delete(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/os/v1/firmware/")) return cancelFirmwareCheckResponse(path);
        if (path.matches("/os/v1/firmware/{environment}/")) return cancelFirmwareCheckResponse(path);
        if (path.matches("/os/v1/firmware/{environment}/{region}/")) return cancelFirmwareCheckResponse(path);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse requestFirmwareCheckResponse(Path path) {
        List<ZoneId> zones = zonesAt(path);
        if (zones.isEmpty())
            return ErrorResponse.notFoundError("No zones at " + path);

        StringJoiner response = new StringJoiner(", ", "Requested firmware checks in ", ".");
        for (ZoneId zone : zones) {
            controller.configServer().nodeRepository().requestFirmwareCheck(zone);
            response.add(zone.value());
        }
        return new MessageResponse(response.toString());
    }

    private HttpResponse cancelFirmwareCheckResponse(Path path) {
        List<ZoneId> zones = zonesAt(path);
        if (zones.isEmpty())
            return ErrorResponse.notFoundError("No zones at " + path);

        StringJoiner response = new StringJoiner(", ", "Cancelled firmware checks in ", ".");
        for (ZoneId zone : zones) {
            controller.configServer().nodeRepository().cancelFirmwareCheck(zone);
            response.add(zone.value());
        }
        return new MessageResponse(response.toString());
    }

    private List<ZoneId> zonesAt(Path path) {
        ZoneList zones = controller.zoneRegistry().zones().controllerUpgraded();
        if (path.get("region") != null) zones = zones.in(RegionName.from(path.get("region")));
        if (path.get("environment") != null) zones = zones.in(Environment.from(path.get("environment")));
        return zones.ids();
    }

    private Slime setOsVersion(HttpRequest request) {
        Slime requestData = toSlime(request.getData());
        Inspector root = requestData.get();
        Inspector versionField = root.field("version");
        Inspector cloudField = root.field("cloud");
        boolean force = root.field("force").asBool();
        if (!versionField.valid() || !cloudField.valid()) {
            throw new IllegalArgumentException("Fields 'version' and 'cloud' are required");
        }

        CloudName cloud = CloudName.from(cloudField.asString());
        Version target;
        try {
            target = Version.fromString(versionField.asString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid version '" + versionField.asString() + "'", e);
        }

        controller.upgradeOsIn(cloud, target, force);
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
