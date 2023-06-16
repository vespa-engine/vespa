// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Scope;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.routing.RoutingId;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicies;
import com.yahoo.vespa.hosted.controller.routing.context.DeploymentRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.context.DeploymentRoutingContext.ExclusiveDeploymentRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.context.DeploymentRoutingContext.SharedDeploymentRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.context.ExclusiveZoneRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.context.RoutingContext;
import com.yahoo.vespa.hosted.controller.routing.context.SharedZoneRoutingContext;
import com.yahoo.vespa.hosted.controller.routing.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.routing.rotation.RotationRepository;
import com.yahoo.vespa.hosted.rotation.config.RotationsConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * The routing controller encapsulates state and methods for inspecting and manipulating deployment endpoints in a
 * hosted Vespa system.
 *
 * The one-stop shop for all your routing needs!
 *
 * @author mpolden
 */
public class RoutingController {

    private final Controller controller;
    private final RoutingPolicies routingPolicies;
    private final RotationRepository rotationRepository;
    private final BooleanFlag createTokenEndpoint;

    public RoutingController(Controller controller, RotationsConfig rotationsConfig) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.routingPolicies = new RoutingPolicies(controller);
        this.rotationRepository = new RotationRepository(Objects.requireNonNull(rotationsConfig, "rotationsConfig must be non-null"),
                                                         controller.applications(),
                                                         controller.curator());
        this.createTokenEndpoint = Flags.ENABLE_DATAPLANE_PROXY.bindTo(controller.flagSource());
    }

    /** Create a routing context for given deployment */
    public DeploymentRoutingContext of(DeploymentId deployment) {
        if (usesSharedRouting(deployment.zoneId())) {
            return new SharedDeploymentRoutingContext(deployment,
                                                      this,
                                                      controller.serviceRegistry().configServer(),
                                                      controller.clock());
        }
        return new ExclusiveDeploymentRoutingContext(deployment, this);
    }

    /** Create a routing context for given zone */
    public RoutingContext of(ZoneId zone) {
        if (usesSharedRouting(zone)) {
            return new SharedZoneRoutingContext(zone, controller.serviceRegistry().configServer());
        }
        return new ExclusiveZoneRoutingContext(zone, routingPolicies);
    }

    public RoutingPolicies policies() {
        return routingPolicies;
    }

    public RotationRepository rotations() {
        return rotationRepository;
    }

    /** Read and return zone-scoped endpoints for given deployment */
    public EndpointList readEndpointsOf(DeploymentId deployment) {
        boolean addTokenEndpoint = createTokenEndpoint.with(FetchVector.Dimension.APPLICATION_ID, deployment.applicationId().serializedForm()).value();
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        // To discover the cluster name for a zone-scoped endpoint, we need to read routing policies
        for (var policy : routingPolicies.read(deployment)) {
            RoutingMethod routingMethod = controller.zoneRegistry().routingMethod(policy.id().zone());
            endpoints.addAll(policy.zoneEndpointsIn(controller.system(), routingMethod, addTokenEndpoint));
            endpoints.add(policy.regionEndpointIn(controller.system(), routingMethod));
        }
        return EndpointList.copyOf(endpoints);
    }

    /** Read application and return declared endpoints for given instance */
    public EndpointList readDeclaredEndpointsOf(ApplicationId instance) {
        if (SystemApplication.matching(instance).isPresent()) return EndpointList.EMPTY;
        return readDeclaredEndpointsOf(TenantAndApplicationId.from(instance)).instance(instance.instance());
    }

    /** Read application and return declared endpoints for given application */
    public EndpointList readDeclaredEndpointsOf(TenantAndApplicationId application) {
        return declaredEndpointsOf(controller.applications().requireApplication(application));
    }

    /** Returns endpoints declared in {@link DeploymentSpec} for given application */
    public EndpointList declaredEndpointsOf(Application application) {
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        DeploymentSpec deploymentSpec = application.deploymentSpec();
        for (var spec : deploymentSpec.instances()) {
            ApplicationId instance = application.id().instance(spec.name());
            // Add endpoint declared with legacy syntax
            spec.globalServiceId().ifPresent(clusterId -> {
                List<DeploymentId> deployments = spec.zones().stream()
                                                     .filter(zone -> zone.concerns(Environment.prod))
                                                     .map(zone -> new DeploymentId(instance, ZoneId.from(Environment.prod, zone.region().get())))
                                                     .toList();
                RoutingId routingId = RoutingId.of(instance, EndpointId.defaultId());
                endpoints.addAll(computeGlobalEndpoints(routingId, ClusterSpec.Id.from(clusterId), deployments));
            });
            // Add endpoints declared with current syntax
            spec.endpoints().forEach(declaredEndpoint -> {
                RoutingId routingId = RoutingId.of(instance, EndpointId.of(declaredEndpoint.endpointId()));
                List<DeploymentId> deployments = declaredEndpoint.regions().stream()
                                                                 .map(region -> new DeploymentId(instance,
                                                                                                 ZoneId.from(Environment.prod, region)))
                                                                 .toList();
                endpoints.addAll(computeGlobalEndpoints(routingId, ClusterSpec.Id.from(declaredEndpoint.containerId()), deployments));
            });
        }
        // Add application endpoints
        for (var declaredEndpoint : deploymentSpec.endpoints()) {
            Map<DeploymentId, Integer> deployments = declaredEndpoint.targets().stream()
                                                                     .collect(toMap(t -> new DeploymentId(application.id().instance(t.instance()),
                                                                                                          ZoneId.from(Environment.prod, t.region())),
                                                                                    t -> t.weight()));

            ZoneId zone = deployments.keySet().iterator().next().zoneId(); // Where multiple zones are possible, they all have the same routing method.
            // Application endpoints are only supported when using direct routing methods
            RoutingMethod routingMethod = usesSharedRouting(zone) ? RoutingMethod.sharedLayer4 : RoutingMethod.exclusive;
            endpoints.add(Endpoint.of(application.id())
                                  .targetApplication(EndpointId.of(declaredEndpoint.endpointId()),
                                                     ClusterSpec.Id.from(declaredEndpoint.containerId()),
                                                     deployments)
                                  .routingMethod(routingMethod)
                                  .on(Port.fromRoutingMethod(routingMethod))
                                  .in(controller.system()));
        }
        return EndpointList.copyOf(endpoints);
    }

    /** Read test runner endpoints for given deployments, grouped by their zone */
    public Map<ZoneId, List<Endpoint>> readTestRunnerEndpointsOf(Collection<DeploymentId> deployments) {
        TreeMap<ZoneId, List<Endpoint>> endpoints = new TreeMap<>(Comparator.comparing(ZoneId::value));
        for (var deployment : deployments) {
            EndpointList zoneEndpoints = readEndpointsOf(deployment).scope(Endpoint.Scope.zone)
                                                                    .not().legacy();
            EndpointList directEndpoints = zoneEndpoints.direct();
            if (!directEndpoints.isEmpty()) {
                zoneEndpoints = directEndpoints; // Use only direct endpoints if we have any
            }
            if  ( ! zoneEndpoints.isEmpty()) {
                endpoints.put(deployment.zoneId(), zoneEndpoints.asList());
            }
        }
        return Collections.unmodifiableSortedMap(endpoints);
    }

    /** Returns certificate DNS names (CN and SAN values) for given deployment */
    public List<String> certificateDnsNames(DeploymentId deployment, DeploymentSpec deploymentSpec) {
        List<String> endpointDnsNames = new ArrayList<>();

        // We add first an endpoint name based on a hash of the application ID,
        // as the certificate provider requires the first CN to be < 64 characters long.
        endpointDnsNames.add(commonNameHashOf(deployment.applicationId(), controller.system()));

        List<Endpoint.EndpointBuilder> builders = new ArrayList<>();
        if (deployment.zoneId().environment().isProduction()) {
            // Add default and wildcard names for global endpoints
            builders.add(Endpoint.of(deployment.applicationId()).target(EndpointId.defaultId()));
            builders.add(Endpoint.of(deployment.applicationId()).wildcard());

            // Add default and wildcard names for each region targeted by application endpoints
            List<DeploymentId> deploymentTargets = deploymentSpec.endpoints().stream()
                                                                 .map(com.yahoo.config.application.api.Endpoint::targets)
                                                                 .flatMap(Collection::stream)
                                                                 .map(com.yahoo.config.application.api.Endpoint.Target::region)
                                                                 .distinct()
                                                                 .map(region -> new DeploymentId(deployment.applicationId(), ZoneId.from(Environment.prod, region)))
                                                                 .toList();
            TenantAndApplicationId application = TenantAndApplicationId.from(deployment.applicationId());
            for (var targetDeployment : deploymentTargets) {
                builders.add(Endpoint.of(application).targetApplication(EndpointId.defaultId(), targetDeployment));
                builders.add(Endpoint.of(application).wildcardApplication(targetDeployment));
            }
        }

        // Add default and wildcard names for zone endpoints
        builders.add(Endpoint.of(deployment.applicationId()).target(ClusterSpec.Id.from("default"), deployment));
        builders.add(Endpoint.of(deployment.applicationId()).wildcard(deployment));

        // Build all certificate names
        for (var builder : builders) {
            Endpoint endpoint = builder.certificateName()
                                       .routingMethod(RoutingMethod.exclusive)
                                       .on(Port.tls())
                                       .in(controller.system());
            endpointDnsNames.add(endpoint.dnsName());
        }
        return Collections.unmodifiableList(endpointDnsNames);
    }

    /** Returns the global and application-level endpoints for given deployment, as container endpoints */
    public Set<ContainerEndpoint> containerEndpointsOf(LockedApplication application, InstanceName instanceName, ZoneId zone) {
        // Assign rotations to application
        for (var deploymentInstanceSpec : application.get().deploymentSpec().instances()) {
            if (deploymentInstanceSpec.concerns(Environment.prod)) {
                application = controller.routing().assignRotations(application, deploymentInstanceSpec.name());
            }
        }

        // Add endpoints backed by a rotation, and register them in DNS if necessary
        boolean registerLegacyNames = requiresLegacyNames(application.get().deploymentSpec(), instanceName);
        Instance instance = application.get().require(instanceName);
        Set<ContainerEndpoint> containerEndpoints = new HashSet<>();
        DeploymentId deployment = new DeploymentId(instance.id(), zone);
        EndpointList endpoints = declaredEndpointsOf(application.get()).targets(deployment);
        EndpointList globalEndpoints = endpoints.scope(Endpoint.Scope.global);
        for (var assignedRotation : instance.rotations()) {
            EndpointList rotationEndpoints = globalEndpoints.named(assignedRotation.endpointId(), Scope.global)
                                                            .requiresRotation();

            // Skip rotations which do not apply to this zone. Legacy names always point to all zones
            if (!registerLegacyNames && !assignedRotation.regions().contains(zone.region())) {
                continue;
            }

            // Omit legacy DNS names when assigning rotations using <endpoints/> syntax
            if (!registerLegacyNames) {
                rotationEndpoints = rotationEndpoints.not().legacy();
            }

            // Register names in DNS
            Rotation rotation = rotationRepository.requireRotation(assignedRotation.rotationId());
            for (var endpoint : rotationEndpoints) {
                controller.nameServiceForwarder().createRecord(
                        new Record(Record.Type.CNAME, RecordName.from(endpoint.dnsName()), RecordData.fqdn(rotation.name())),
                        Priority.normal,
                        Optional.of(application.get().id()));
                List<String> names = List.of(endpoint.dnsName(),
                                             // Include rotation ID as a valid name of this container endpoint
                                             // (required by global routing health checks)
                                             assignedRotation.rotationId().asString());
                containerEndpoints.add(new ContainerEndpoint(assignedRotation.clusterId().value(),
                                                             asString(Endpoint.Scope.global),
                                                             names,
                                                             OptionalInt.empty(),
                                                             endpoint.routingMethod()));
            }
        }
        // Add endpoints not backed by a rotation (i.e. other routing methods so that the config server always knows
        // about global names, even when not using rotations)
        globalEndpoints.not().requiresRotation()
                       .groupingBy(Endpoint::cluster)
                       .forEach((clusterId, clusterEndpoints) -> {
                           containerEndpoints.add(new ContainerEndpoint(clusterId.value(),
                                                                        asString(Endpoint.Scope.global),
                                                                        clusterEndpoints.mapToList(Endpoint::dnsName),
                                                                        OptionalInt.empty(),
                                                                        RoutingMethod.exclusive));
                       });
        // Add application endpoints
        EndpointList applicationEndpoints = endpoints.scope(Endpoint.Scope.application);
        for (var endpoint : applicationEndpoints.shared()) { // DNS for non-shared endpoints is handled by RoutingPolicies
            Set<ZoneId> targetZones = endpoint.targets().stream()
                                              .map(t -> t.deployment().zoneId())
                                              .collect(Collectors.toUnmodifiableSet());
            if (targetZones.size() != 1) throw new IllegalArgumentException("Endpoint '" + endpoint.name() +
                                                                            "' must target a single zone, got " +
                                                                            targetZones);
            ZoneId targetZone = targetZones.iterator().next();
            String vipHostname = controller.zoneRegistry().getVipHostname(targetZone)
                                           .orElseThrow(() -> new IllegalArgumentException("No VIP configured for zone " + targetZone));
            controller.nameServiceForwarder().createRecord(
                    new Record(Record.Type.CNAME, RecordName.from(endpoint.dnsName()), RecordData.fqdn(vipHostname)),
                    Priority.normal,
                    Optional.of(application.get().id()));
            controller.nameServiceForwarder().removeRecords(
                    Record.Type.CNAME, RecordName.from(endpoint.legacyRegionalDnsName()),
                    Priority.normal,
                    Optional.of(application.get().id()));
        }
        Map<ClusterSpec.Id, EndpointList> applicationEndpointsByCluster = applicationEndpoints.groupingBy(Endpoint::cluster);
        for (var kv : applicationEndpointsByCluster.entrySet()) {
            ClusterSpec.Id clusterId = kv.getKey();
            EndpointList clusterEndpoints = kv.getValue();
            for (var endpoint : clusterEndpoints) {
                Optional<Endpoint.Target> matchingTarget = endpoint.targets().stream()
                                                                   .filter(t -> t.routesTo(deployment))
                                                                   .findFirst();
                if (matchingTarget.isEmpty()) throw new IllegalStateException("No target found routing to " + deployment + " in " + endpoint);
                containerEndpoints.add(new ContainerEndpoint(clusterId.value(),
                                                             asString(Endpoint.Scope.application),
                                                             List.of(endpoint.dnsName()),
                                                             OptionalInt.of(matchingTarget.get().weight()),
                                                             endpoint.routingMethod()));
            }
        }
        return Collections.unmodifiableSet(containerEndpoints);
    }

    /** Remove endpoints in DNS for all rotations assigned to given instance */
    public void removeEndpointsInDns(Application application, InstanceName instanceName) {
        Set<Endpoint> endpointsToRemove = new LinkedHashSet<>();
        Instance instance = application.require(instanceName);
        // Compute endpoints from rotations. When removing DNS records for rotation-based endpoints we cannot use the
        // deployment spec, because submitting an empty deployment spec is the first step of removing an application
        for (var rotation : instance.rotations()) {
            var deployments = rotation.regions().stream()
                                      .map(region -> new DeploymentId(instance.id(), ZoneId.from(Environment.prod, region)))
                                      .toList();
            endpointsToRemove.addAll(computeGlobalEndpoints(RoutingId.of(instance.id(), rotation.endpointId()),
                                                            rotation.clusterId(), deployments));
        }
        endpointsToRemove.forEach(endpoint -> controller.nameServiceForwarder()
                                                        .removeRecords(Record.Type.CNAME,
                                                                       RecordName.from(endpoint.dnsName()),
                                                                       Priority.normal,
                                                                       Optional.of(application.id())));
    }

    /**
     * Assigns one or more global rotations to given application, if eligible. The given application is implicitly
     * stored, ensuring that the assigned rotation(s) are persisted when this returns.
     */
    private LockedApplication assignRotations(LockedApplication application, InstanceName instanceName) {
        try (RotationLock rotationLock = rotationRepository.lock()) {
            var rotations = rotationRepository.getOrAssignRotations(application.get().deploymentSpec(),
                                                                    application.get().require(instanceName),
                                                                    rotationLock);
            application = application.with(instanceName, instance -> instance.with(rotations));
            controller.applications().store(application); // store assigned rotation even if deployment fails
        }
        return application;
    }

    private boolean usesSharedRouting(ZoneId zone) {
        return controller.zoneRegistry().routingMethod(zone).isShared();
    }

    /** Returns the routing methods that are available across all given deployments */
    private List<RoutingMethod> routingMethodsOfAll(Collection<DeploymentId> deployments) {
        Map<RoutingMethod, Set<DeploymentId>> deploymentsByMethod = new HashMap<>();
        for (var deployment : deployments) {
            RoutingMethod routingMethod = controller.zoneRegistry().routingMethod(deployment.zoneId());
            deploymentsByMethod.computeIfAbsent(routingMethod, k -> new LinkedHashSet<>())
                               .add(deployment);
        }
        List<RoutingMethod> routingMethods = new ArrayList<>();
        deploymentsByMethod.forEach((method, supportedDeployments) -> {
            if (supportedDeployments.containsAll(deployments)) {
                routingMethods.add(method);
            }
        });
        return Collections.unmodifiableList(routingMethods);
    }

    /** Compute global endpoints for given routing ID, application and deployments */
    private List<Endpoint> computeGlobalEndpoints(RoutingId routingId, ClusterSpec.Id cluster, List<DeploymentId> deployments) {
        var endpoints = new ArrayList<Endpoint>();
        var directMethods = 0;
        var availableRoutingMethods = routingMethodsOfAll(deployments);
        for (var method : availableRoutingMethods) {
            if (method.isDirect() && ++directMethods > 1) {
                throw new IllegalArgumentException("Invalid routing methods for " + routingId + ": Exceeded maximum " +
                                                   "direct methods");
            }
            endpoints.add(Endpoint.of(routingId.instance())
                                  .target(routingId.endpointId(), cluster, deployments)
                                  .on(Port.fromRoutingMethod(method))
                                  .routingMethod(method)
                                  .in(controller.system()));
        }
        return endpoints;
    }

    /** Whether legacy global DNS names should be available for given application */
    private static boolean requiresLegacyNames(DeploymentSpec deploymentSpec, InstanceName instanceName) {
        return deploymentSpec.instance(instanceName)
                             .flatMap(DeploymentInstanceSpec::globalServiceId)
                             .isPresent();
    }

    /** Create a common name based on a hash of given application. This must be less than 64 characters long. */
    private static String commonNameHashOf(ApplicationId application, SystemName system) {
        @SuppressWarnings("deprecation") // for Hashing.sha1()
        HashCode sha1 = Hashing.sha1().hashString(application.serializedForm(), StandardCharsets.UTF_8);
        String base32 = BaseEncoding.base32().omitPadding().lowerCase().encode(sha1.asBytes());
        return 'v' + base32 + Endpoint.internalDnsSuffix(system);
    }

    private static String asString(Endpoint.Scope scope) {
        return switch (scope) {
            case application -> "application";
            case global -> "global";
            case weighted -> "weighted";
            case zone -> "zone";
        };
    }

}
