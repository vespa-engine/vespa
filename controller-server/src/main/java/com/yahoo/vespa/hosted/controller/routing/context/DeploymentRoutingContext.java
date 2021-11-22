// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing.context;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.RoutingController;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A deployment routing context, which extends {@link RoutingContext} to support routing configuration of a deployment.
 *
 * @author mpolden
 */
public abstract class DeploymentRoutingContext implements RoutingContext {

    final DeploymentId deployment;
    final RoutingController controller;
    final RoutingMethod method;

    public DeploymentRoutingContext(DeploymentId deployment, RoutingMethod method, RoutingController controller) {
        this.deployment = Objects.requireNonNull(deployment);
        this.controller = Objects.requireNonNull(controller);
        this.method = Objects.requireNonNull(method);
    }

    /**
     * Prepare routing configuration for the deployment in this context
     *
     * @return the container endpoints relevant for this deployment, as declared in deployment spec
     */
    public final Set<ContainerEndpoint> prepare(LockedApplication application) {
        return controller.containerEndpointsOf(application, deployment.applicationId().instance(), deployment.zoneId());
    }

    /** Configure routing for the deployment in this context, using given deployment spec */
    public final void configure(DeploymentSpec deploymentSpec) {
        controller.policies().refresh(deployment.applicationId(), deploymentSpec, deployment.zoneId());
    }

    /** Routing method of this context */
    public final RoutingMethod routingMethod() {
        return method;
    }

    /** Read the routing policy for given cluster in this deployment */
    public final Optional<RoutingPolicy> routingPolicy(ClusterSpec.Id cluster) {
        RoutingPolicyId id = new RoutingPolicyId(deployment.applicationId(), cluster, deployment.zoneId());
        return Optional.ofNullable(controller.policies().get(deployment).get(id));
    }

    /**
     * Extension of a {@link DeploymentRoutingContext} for deployments using either {@link RoutingMethod#shared} or
     * {@link RoutingMethod#sharedLayer4} routing.
     */
    public static class SharedDeploymentRoutingContext extends DeploymentRoutingContext {

        private final Clock clock;
        private final ConfigServer configServer;

        public SharedDeploymentRoutingContext(DeploymentId deployment, RoutingController controller, ConfigServer configServer, Clock clock) {
            super(deployment, RoutingMethod.shared, controller);
            this.clock = Objects.requireNonNull(clock);
            this.configServer = Objects.requireNonNull(configServer);
        }

        @Override
        public void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
            EndpointStatus newStatus = new EndpointStatus(value == RoutingStatus.Value.in
                                                                  ? EndpointStatus.Status.in
                                                                  : EndpointStatus.Status.out,
                                                          "",
                                                          agent.name(),
                                                          clock.instant().getEpochSecond());
            primaryEndpoint().ifPresent(endpoint -> {
                try {
                    configServer.setGlobalRotationStatus(deployment, endpoint.upstreamIdOf(deployment), newStatus);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to set rotation status of " + endpoint + " in " + deployment, e);
                }
            });
        }

        @Override
        public RoutingStatus routingStatus() {
            Optional<EndpointStatus> status = primaryEndpoint().map(endpoint -> {
                var upstreamName = endpoint.upstreamIdOf(deployment);
                return configServer.getGlobalRotationStatus(deployment, upstreamName);
            });
            if (status.isEmpty()) return RoutingStatus.DEFAULT;
            RoutingStatus.Agent agent;
            try {
                agent = RoutingStatus.Agent.valueOf(status.get().getAgent().toLowerCase());
            } catch (IllegalArgumentException e) {
                agent = RoutingStatus.Agent.unknown;
            }
            return new RoutingStatus(status.get().getStatus() == EndpointStatus.Status.in
                                             ? RoutingStatus.Value.in
                                             : RoutingStatus.Value.out,
                                     agent,
                                     Instant.ofEpochSecond(status.get().getEpoch()));
        }

        private Optional<Endpoint> primaryEndpoint() {
            return controller.readDeclaredEndpointsOf(deployment.applicationId())
                             .requiresRotation()
                             .primary();
        }

    }

    /**
     * Implementation of a {@link DeploymentRoutingContext} for deployments using {@link RoutingMethod#exclusive}
     * routing.
     */
    public static class ExclusiveDeploymentRoutingContext extends DeploymentRoutingContext {

        public ExclusiveDeploymentRoutingContext(DeploymentId deployment, RoutingController controller) {
            super(deployment, RoutingMethod.exclusive, controller);
        }

        @Override
        public void setRoutingStatus(RoutingStatus.Value value, RoutingStatus.Agent agent) {
            controller.policies().setRoutingStatus(deployment, value, agent);
        }

        @Override
        public RoutingStatus routingStatus() {
            // Status for a deployment applies to all clusters within the deployment, so we use the status from the
            // first matching policy here
            return controller.policies().get(deployment).values().stream()
                             .findFirst()
                             .map(RoutingPolicy::status)
                             .map(RoutingPolicy.Status::routingStatus)
                             .orElse(RoutingStatus.DEFAULT);
        }

    }

}
