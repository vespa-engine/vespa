// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v1;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.path.Path;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This implements the /routing/v1/status REST API on the config server, providing explicit control over the routing
 * status of a deployment or zone (all deployments). The routing status manipulated by this is only respected by the
 * shared routing layer.
 *
 * @author bjorncs
 * @author mpolden
 */
public class RoutingStatusApiHandler extends RestApiRequestHandler<RoutingStatusApiHandler> {

    private static final Logger log = Logger.getLogger(RoutingStatusApiHandler.class.getName());

    private static final Path ROUTING_ROOT = Path.fromString("/routing/v1/");
    private static final Path DEPLOYMENT_STATUS_ROOT = ROUTING_ROOT.append("status");
    private static final Path ZONE_STATUS = ROUTING_ROOT.append("zone-inactive");

    private final Curator curator;
    private final Clock clock;

    @Inject
    public RoutingStatusApiHandler(Context context, Curator curator) {
        this(context, curator, Clock.systemUTC());
    }

    RoutingStatusApiHandler(Context context, Curator curator, Clock clock) {
        super(context, RoutingStatusApiHandler::createRestApiDefinition);
        this.curator = Objects.requireNonNull(curator);
        this.clock = Objects.requireNonNull(clock);

        curator.create(DEPLOYMENT_STATUS_ROOT);
    }

    private static RestApi createRestApiDefinition(RoutingStatusApiHandler self) {
        return RestApi.builder()
                // TODO(mpolden): Remove this route when clients have migrated to v2
                .addRoute(RestApi.route("/routing/v1/status")
                    .get(self::listInactiveDeployments))
                .addRoute(RestApi.route("/routing/v1/status/zone")
                    .get(self::zoneStatus)
                    .put(self::changeZoneStatus)
                    .delete(self::changeZoneStatus))
                .addRoute(RestApi.route("/routing/v1/status/{upstreamName}")
                    .get(self::getDeploymentStatus)
                    .put(self::changeDeploymentStatus))
                .addRoute(RestApi.route("/routing/v2/status")
                                 .get(self::getDeploymentStatusV2))
                .build();
    }

    /* Get inactive deployments and zone status */
    private SlimeJsonResponse getDeploymentStatusV2(RestApi.RequestContext context) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor inactiveDeploymentsArray = root.setArray("inactiveDeployments");
        curator.getChildren(DEPLOYMENT_STATUS_ROOT).stream()
               .filter(upstreamName -> deploymentStatus(upstreamName).status() == RoutingStatus.out)
               .sorted()
               .forEach(upstreamName -> {
                   Cursor deploymentObject = inactiveDeploymentsArray.addObject();
                   deploymentObject.setString("upstreamName", upstreamName);
               });
        root.setBool("zoneActive", zoneStatus() == RoutingStatus.in);
        return new SlimeJsonResponse(slime);
    }

    /** Get upstream of all deployments with status OUT */
    private SlimeJsonResponse listInactiveDeployments(RestApi.RequestContext context) {
        List<String> inactiveDeployments = curator.getChildren(DEPLOYMENT_STATUS_ROOT).stream()
                                                  .filter(upstreamName -> deploymentStatus(upstreamName).status() == RoutingStatus.out)
                                                  .sorted()
                                                  .collect(Collectors.toUnmodifiableList());
        Slime slime = new Slime();
        Cursor rootArray = slime.setArray();
        inactiveDeployments.forEach(rootArray::addString);
        return new SlimeJsonResponse(slime);
    }

    /** Get the routing status of a deployment */
    private SlimeJsonResponse getDeploymentStatus(RestApi.RequestContext context) {
        String upstreamName = upstreamName(context);
        DeploymentRoutingStatus deploymentRoutingStatus = deploymentStatus(upstreamName);
        // If the entire zone is out, we always return OUT regardless of the actual routing status
        if (zoneStatus() == RoutingStatus.out) {
            String reason = String.format("Rotation is OUT because the zone is OUT (actual deployment status is %s)",
                                          deploymentRoutingStatus.status().name().toUpperCase(Locale.ENGLISH));
            deploymentRoutingStatus = new DeploymentRoutingStatus(RoutingStatus.out, "operator", reason,
                                                                  clock.instant());
        }
        return new SlimeJsonResponse(toSlime(deploymentRoutingStatus));
    }

    /** Change routing status of a deployment */
    private SlimeJsonResponse changeDeploymentStatus(RestApi.RequestContext context) {
        Set<String> upstreamNames = upstreamNames(context);
        ApplicationId instance = instance(context);
        RestApi.RequestContext.RequestContent requestContent = context.requestContentOrThrow();
        Slime requestBody = Exceptions.uncheck(() -> SlimeUtils.jsonToSlime(requestContent.content().readAllBytes()));
        DeploymentRoutingStatus wantedStatus = deploymentRoutingStatusFromSlime(requestBody, clock.instant());
        List<DeploymentRoutingStatus> currentStatuses = upstreamNames.stream()
                                                                     .map(this::deploymentStatus)
                                                                     .collect(Collectors.toList());
        DeploymentRoutingStatus currentStatus = currentStatuses.get(0);
        log.log(Level.INFO, "Changing routing status of " + instance + " from " +
                            currentStatus.status() + " to " + wantedStatus.status());
        boolean needsChange = currentStatuses.stream().anyMatch(status -> status.status() != wantedStatus.status());
        if (needsChange) {
            changeStatus(upstreamNames, wantedStatus);
        }
        return new SlimeJsonResponse(toSlime(wantedStatus));
    }

    /** Change routing status of a zone */
    private SlimeJsonResponse changeZoneStatus(RestApi.RequestContext context) {
        boolean in = context.request().getMethod() == HttpRequest.Method.DELETE;
        log.log(Level.INFO, "Changing routing status of zone from " + zoneStatus() + " to " +
                            (in ? RoutingStatus.in : RoutingStatus.out));
        if (in) {
            curator.delete(ZONE_STATUS);
            return new SlimeJsonResponse(toSlime(RoutingStatus.in));
        } else {
            curator.create(ZONE_STATUS);
            return new SlimeJsonResponse(toSlime(RoutingStatus.out));
        }
    }

    /** Read the status for zone */
    private SlimeJsonResponse zoneStatus(RestApi.RequestContext context) {
        return new SlimeJsonResponse(toSlime(zoneStatus()));
    }

    /** Change the status of one or more upstream names */
    private void changeStatus(Set<String> upstreamNames, DeploymentRoutingStatus newStatus) {
        CuratorTransaction transaction = new CuratorTransaction(curator);
        for (var upstreamName : upstreamNames) {
            Path path = deploymentStatusPath(upstreamName);
            if (curator.exists(path)) {
                transaction.add(CuratorOperations.delete(path.getAbsolute()));
            }
            transaction.add(CuratorOperations.create(path.getAbsolute(), toJsonBytes(newStatus)));
        }
        transaction.commit();
    }

    /** Read the status for a deployment */
    private DeploymentRoutingStatus deploymentStatus(String upstreamName) {
        Instant changedAt = clock.instant();
        Path path = deploymentStatusPath(upstreamName);
        Optional<byte[]> data = curator.getData(path);
        if (data.isEmpty()) {
            return new DeploymentRoutingStatus(RoutingStatus.in, "", "", changedAt);
        }
        String agent = "";
        String reason = "";
        RoutingStatus status = RoutingStatus.out;
        if (data.get().length > 0) { // Compatibility with old format, where no data is stored
            Slime slime = SlimeUtils.jsonToSlime(data.get());
            Cursor root = slime.get();
            status = asRoutingStatus(root.field("status").asString());
            agent = root.field("agent").asString();
            reason = root.field("cause").asString();
            changedAt = Instant.ofEpochSecond(root.field("lastUpdate").asLong());
        }
        return new DeploymentRoutingStatus(status, agent, reason, changedAt);
    }

    private RoutingStatus zoneStatus() {
        return curator.exists(ZONE_STATUS) ? RoutingStatus.out : RoutingStatus.in;
    }

    protected Path deploymentStatusPath(String upstreamName) {
        return DEPLOYMENT_STATUS_ROOT.append(upstreamName);
    }

    private static String upstreamName(RestApi.RequestContext context) {
        return upstreamNames(context).iterator().next();
    }

    private static Set<String> upstreamNames(RestApi.RequestContext context) {
        Set<String> upstreamNames = Arrays.stream(context.pathParameters().getStringOrThrow("upstreamName")
                                                         .split(","))
                                          .collect(Collectors.toSet());
        if (upstreamNames.isEmpty()) {
            throw new RestApiException.BadRequest("At least one upstream name must be specified");
        }
        for (var upstreamName : upstreamNames) {
            if (upstreamName.contains(" ")) {
                throw new RestApiException.BadRequest("Invalid upstream name: '" + upstreamName + "'");
            }
        }
        return upstreamNames;
    }

    private static ApplicationId instance(RestApi.RequestContext context) {
        return context.queryParameters().getString("application")
                      .map(ApplicationId::fromSerializedForm)
                      .orElseThrow(() -> new RestApiException.BadRequest("Missing application parameter"));
    }

    private byte[] toJsonBytes(DeploymentRoutingStatus status) {
        return Exceptions.uncheck(() -> SlimeUtils.toJsonBytes(toSlime(status)));
    }

    private Slime toSlime(DeploymentRoutingStatus status) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("status", asString(status.status()));
        root.setString("cause", status.reason());
        root.setString("agent", status.agent());
        root.setLong("lastUpdate", status.changedAt().getEpochSecond());
        return slime;
    }

    private static Slime toSlime(RoutingStatus status) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("status", asString(status));
        return slime;
    }

    private static RoutingStatus asRoutingStatus(String s) {
        switch (s) {
            case "IN": return RoutingStatus.in;
            case "OUT": return RoutingStatus.out;
        }
        throw new IllegalArgumentException("Unknown status: '" + s + "'");
    }

    private static String asString(RoutingStatus status) {
        switch (status) {
            case in: return "IN";
            case out: return "OUT";
        }
        throw new IllegalArgumentException("Unknown status: " + status);
    }

    private static DeploymentRoutingStatus deploymentRoutingStatusFromSlime(Slime slime, Instant changedAt) {
        Cursor root = slime.get();
        return new DeploymentRoutingStatus(asRoutingStatus(root.field("status").asString()),
                                           root.field("agent").asString(),
                                           root.field("cause").asString(),
                                           changedAt);
    }

    private static class DeploymentRoutingStatus {

        private final RoutingStatus status;
        private final String agent;
        private final String reason;
        private final Instant changedAt;

        public DeploymentRoutingStatus(RoutingStatus status, String agent, String reason, Instant changedAt) {
            this.status = Objects.requireNonNull(status);
            this.agent = Objects.requireNonNull(agent);
            this.reason = Objects.requireNonNull(reason);
            this.changedAt = Objects.requireNonNull(changedAt);
        }

        public RoutingStatus status() {
            return status;
        }

        public String agent() {
            return agent;
        }

        public String reason() {
            return reason;
        }

        public Instant changedAt() {
            return changedAt;
        }

    }

    private enum RoutingStatus {
        in, out
    }

}
