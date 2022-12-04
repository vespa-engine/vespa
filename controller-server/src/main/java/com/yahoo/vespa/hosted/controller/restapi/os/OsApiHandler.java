// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLoggingRequestHandler;
import com.yahoo.vespa.hosted.controller.maintenance.ControllerMaintenance;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgradeScheduler;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgradeScheduler.Change;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This implements the /os/v1 API which provides operators with information about, and scheduling of OS upgrades for
 * nodes in the system.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class OsApiHandler extends AuditLoggingRequestHandler {

    private final Controller controller;
    private final OsUpgradeScheduler osUpgradeScheduler;

    public OsApiHandler(Context ctx, Controller controller, ControllerMaintenance controllerMaintenance) {
        super(ctx, controller.auditLogger());
        this.controller = controller;
        this.osUpgradeScheduler = controllerMaintenance.osUpgradeScheduler();
    }

    @Override
    public HttpResponse auditAndHandle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> get(request);
                case POST -> post(request);
                case DELETE -> delete(request);
                case PATCH -> patch(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            };
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse patch(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/os/v1/")) return setOsVersion(request);
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
            controller.serviceRegistry().configServer().nodeRepository().requestFirmwareCheck(zone);
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
            controller.serviceRegistry().configServer().nodeRepository().cancelFirmwareCheck(zone);
            response.add(zone.value());
        }
        return new MessageResponse(response.toString());
    }

    private List<ZoneId> zonesAt(Path path) {
        ZoneList zones = controller.zoneRegistry().zones().controllerUpgraded();
        if (path.get("region") != null) zones = zones.in(RegionName.from(path.get("region")));
        if (path.get("environment") != null) zones = zones.in(Environment.from(path.get("environment")));
        return zones.zones().stream().map(ZoneApi::getId).collect(Collectors.toList());
    }

    private HttpResponse setOsVersion(HttpRequest request) {
        Slime requestData = toSlime(request.getData());
        Inspector root = requestData.get();
        CloudName cloud = parseStringField("cloud", root, CloudName::from);
        if (requireField("version", root).type() == Type.NIX) {
            controller.cancelOsUpgradeIn(cloud);
            return new MessageResponse("Cleared target OS version for cloud '" + cloud.value() + "'");
        }
        Version target = parseStringField("version", root, Version::fromString);
        boolean force = root.field("force").asBool();
        controller.upgradeOsIn(cloud, target, force);
        return new MessageResponse("Set target OS version for cloud '" + cloud.value() + "' to " +
                                   target.toFullString());
    }

    private Slime osVersions() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Set<OsVersionTarget> targets = controller.osVersionTargets();

        Cursor versions = root.setArray("versions");
        Instant now = controller.clock().instant();
        controller.osVersionStatus().versions().forEach((osVersion, nodeVersions) -> {
            Cursor currentVersionObject = versions.addObject();
            currentVersionObject.setString("version", osVersion.version().toFullString());
            Optional<OsVersionTarget> target = targets.stream().filter(t -> t.osVersion().equals(osVersion)).findFirst();
            currentVersionObject.setBool("targetVersion", target.isPresent());
            target.ifPresent(t -> {
                currentVersionObject.setString("upgradeBudget", Duration.ZERO.toString());
                currentVersionObject.setLong("scheduledAt", t.scheduledAt().toEpochMilli());
                Optional<Change> nextChange = osUpgradeScheduler.changeIn(t.osVersion().cloud(), now);
                nextChange.ifPresent(c -> {
                    currentVersionObject.setString("nextVersion", c.version().toFullString());
                    currentVersionObject.setLong("nextScheduledAt", c.scheduleAt().toEpochMilli());
                });
            });

            currentVersionObject.setString("cloud", osVersion.cloud().value());
            Cursor nodesArray = currentVersionObject.setArray("nodes");
            nodeVersions.forEach(nodeVersion -> {
                Cursor nodeObject = nodesArray.addObject();
                nodeObject.setString("hostname", nodeVersion.hostname().value());
                nodeObject.setString("environment", nodeVersion.zone().environment().value());
                nodeObject.setString("region", nodeVersion.zone().region().value());
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

    private static <T> T parseStringField(String name, Inspector root, Function<String, T> parser) {
        String fieldValue = requireField(name, root).asString();
        try {
            return parser.apply(fieldValue);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + name + " '" + fieldValue + "'", e);
        }
    }

    private static Inspector requireField(String name, Inspector root) {
        Inspector field = root.field(name);
        if (!field.valid()) throw new IllegalArgumentException("Field '" + name + "' is required");
        return field;
    }

}
