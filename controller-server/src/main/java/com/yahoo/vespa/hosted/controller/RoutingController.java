// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.AuthMethod;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Scope;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.application.GeneratedEndpoint;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.BasicServicesXml;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.routing.EndpointConfig;
import com.yahoo.vespa.hosted.controller.routing.GeneratedEndpointList;
import com.yahoo.vespa.hosted.controller.routing.PreparedEndpoints;
import com.yahoo.vespa.hosted.controller.routing.RoutingId;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicies;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyList;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * The routing controller is owned by {@link Controller} and encapsulates state and methods for inspecting and
 * manipulating deployment endpoints in a hosted Vespa system.
 *
 * The one-stop shop for all your routing needs!
 *
 * @author mpolden
 */
public class RoutingController {

    private static final Logger LOG = Logger.getLogger(RoutingController.class.getName());

    private final Controller controller;
    private final RoutingPolicies routingPolicies;
    private final RotationRepository rotationRepository;
    private final StringFlag endpointConfig;

    public RoutingController(Controller controller, RotationsConfig rotationsConfig) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.routingPolicies = new RoutingPolicies(controller);
        this.rotationRepository = new RotationRepository(Objects.requireNonNull(rotationsConfig, "rotationsConfig must be non-null"),
                                                         controller.applications(),
                                                         controller.curator());
        this.endpointConfig = Flags.ENDPOINT_CONFIG.bindTo(controller.flagSource());
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

    /** Returns the endpoint config to use for given instance */
    public EndpointConfig endpointConfig(ApplicationId instance) {
        String flagValue = endpointConfig.with(FetchVector.Dimension.TENANT_ID, instance.tenant().value())
                                         .with(FetchVector.Dimension.APPLICATION, TenantAndApplicationId.from(instance).serialized())
                                         .with(FetchVector.Dimension.INSTANCE_ID, instance.serializedForm())
                                         .value();
        return switch (flagValue) {
            case "legacy" -> EndpointConfig.legacy;
            case "combined" -> EndpointConfig.combined;
            case "generated" -> EndpointConfig.generated;
            default -> throw new IllegalArgumentException("Invalid endpoint-config flag value: '" + flagValue + "', must be " +
                                                          "'legacy', 'combined' or 'generated'");
        };
    }

    /** Prepares and returns the endpoints relevant for given deployment */
    public PreparedEndpoints prepare(DeploymentId deployment, BasicServicesXml services, EndpointCertificate certificate, LockedApplication application) {
        EndpointList endpoints = EndpointList.EMPTY;
        DeploymentSpec spec = application.get().deploymentSpec();

        // Assign rotations to application
        for (var instanceSpec : spec.instances()) {
            if (instanceSpec.concerns(Environment.prod)) {
                application = controller.routing().assignRotations(application, instanceSpec.name());
            }
        }

        // Add zone-scoped endpoints
        Map<EndpointId, List<GeneratedEndpoint>> generatedForDeclaredEndpoints = new HashMap<>();
        Set<ClusterSpec.Id> clustersWithToken = new HashSet<>();
        EndpointConfig config = endpointConfig(deployment.applicationId());
        RoutingPolicyList applicationPolicies = policies().read(TenantAndApplicationId.from(deployment.applicationId()));
        RoutingPolicyList deploymentPolicies = applicationPolicies.deployment(deployment);
        for (var container : services.containers()) {
            ClusterSpec.Id clusterId = ClusterSpec.Id.from(container.id());
            boolean tokenSupported = container.authMethods().contains(BasicServicesXml.Container.AuthMethod.token);
            if (tokenSupported) {
                clustersWithToken.add(clusterId);
            }
            Optional<RoutingPolicy> clusterPolicy = deploymentPolicies.cluster(clusterId).first();
            List<GeneratedEndpoint> generatedForCluster = clusterPolicy.map(policy -> policy.generatedEndpoints().cluster().asList())
                                                                       .orElseGet(List::of);
            // Generate endpoint for each auth method, if not present
            generatedForCluster = generateEndpoints(AuthMethod.mtls, certificate, Optional.empty(), generatedForCluster);
            if (tokenSupported) {
                generatedForCluster = generateEndpoints(AuthMethod.token, certificate, Optional.empty(), generatedForCluster);
            }
            GeneratedEndpointList generatedEndpoints = config.supportsGenerated() ? GeneratedEndpointList.copyOf(generatedForCluster) : GeneratedEndpointList.EMPTY;
            endpoints = endpoints.and(endpointsOf(deployment, clusterId, generatedEndpoints).scope(Scope.zone));
        }

        // Add global- and application-scoped endpoints
        for (var container : services.containers()) {
            ClusterSpec.Id clusterId = ClusterSpec.Id.from(container.id());
            applicationPolicies.cluster(clusterId).asList().stream()
                               .flatMap(policy -> policy.generatedEndpoints().declared().asList().stream())
                               .forEach(ge -> {
                                   List<GeneratedEndpoint> generated = generatedForDeclaredEndpoints.computeIfAbsent(ge.endpoint().get(), (k) -> new ArrayList<>());
                                   if (!generated.contains(ge)) {
                                       generated.add(ge);
                                   }
                               });
        }
        // Generate endpoints if declared endpoint does not have any
        Stream.concat(spec.endpoints().stream(), spec.instances().stream().flatMap(i -> i.endpoints().stream()))
              .forEach(endpoint -> {
                  EndpointId endpointId = EndpointId.of(endpoint.endpointId());
                  generatedForDeclaredEndpoints.compute(endpointId, (k, old) -> {
                      if (old == null) {
                          old = List.of();
                      }
                      List<GeneratedEndpoint> generatedEndpoints = generateEndpoints(AuthMethod.mtls, certificate, Optional.of(endpointId), old);
                      boolean tokenSupported = clustersWithToken.contains(ClusterSpec.Id.from(endpoint.containerId()));
                      if (tokenSupported){
                          generatedEndpoints = generateEndpoints(AuthMethod.token, certificate, Optional.of(endpointId), generatedEndpoints);
                      }
                      return generatedEndpoints;
                  });
              });
        Map<EndpointId, GeneratedEndpointList> generatedEndpoints = config.supportsGenerated()
                ? generatedForDeclaredEndpoints.entrySet()
                                               .stream()
                                               .collect(Collectors.toMap(Map.Entry::getKey, kv -> GeneratedEndpointList.copyOf(kv.getValue())))
                : Map.of();
        endpoints = endpoints.and(declaredEndpointsOf(application.get().id(), spec, generatedEndpoints).targets(deployment));
        PreparedEndpoints prepared = new PreparedEndpoints(deployment,
                                                           endpoints,
                                                           application.get().require(deployment.applicationId().instance()).rotations(),
                                                           certificate);

        // Register rotation-backed endpoints in DNS
        registerRotationEndpointsInDns(prepared);

        LOG.log(Level.FINE, () -> "Prepared endpoints: " + prepared);

        return prepared;
    }

    // -------------- Implicit endpoints (scopes 'zone' and 'weighted') --------------

    /** Returns the zone- and region-scoped endpoints of given deployment */
    public EndpointList endpointsOf(DeploymentId deployment, ClusterSpec.Id cluster, GeneratedEndpointList generatedEndpoints) {
        requireGeneratedEndpoints(generatedEndpoints, false);
        boolean generatedEndpointsAvailable = !generatedEndpoints.isEmpty();
        boolean tokenSupported = !generatedEndpoints.authMethod(AuthMethod.token).isEmpty();
        boolean isProduction = deployment.zoneId().environment().isProduction();
        RoutingMethod routingMethod = controller.zoneRegistry().routingMethod(deployment.zoneId());
        List<Endpoint> endpoints = new ArrayList<>();
        Endpoint.EndpointBuilder zoneEndpoint = Endpoint.of(deployment.applicationId())
                                                        .routingMethod(routingMethod)
                                                        .on(Port.fromRoutingMethod(routingMethod))
                                                        .legacy(generatedEndpointsAvailable)
                                                        .target(cluster, deployment);
        endpoints.add(zoneEndpoint.in(controller.system()));
        ZoneApi zone = controller.zoneRegistry().zones().all().get(deployment.zoneId()).get();
        Endpoint.EndpointBuilder regionEndpoint = Endpoint.of(deployment.applicationId())
                                                          .routingMethod(routingMethod)
                                                          .on(Port.fromRoutingMethod(routingMethod))
                                                          .legacy(generatedEndpointsAvailable)
                                                          .targetRegion(cluster,
                                                                        zone.getCloudNativeRegionName(),
                                                                        zone.getCloudName());
        // Region endpoints are only used by global- and application-endpoints and are thus only needed in
        // production environments
        if (isProduction) {
            endpoints.add(regionEndpoint.in(controller.system()));
        }
        for (var generatedEndpoint : generatedEndpoints) {
            boolean include = switch (generatedEndpoint.authMethod()) {
                case token -> tokenSupported;
                case mtls -> true;
                case none -> false;
            };
            if (include) {
                endpoints.add(zoneEndpoint.generatedFrom(generatedEndpoint)
                                          .legacy(false)
                                          .authMethod(generatedEndpoint.authMethod())
                                          .in(controller.system()));
                // Only a single region endpoint is needed, not one per auth method
                if (isProduction && generatedEndpoint.authMethod() == AuthMethod.mtls) {
                    GeneratedEndpoint weightedGeneratedEndpoint = generatedEndpoint.withClusterPart(weightedClusterPart(cluster, deployment));
                    endpoints.add(regionEndpoint.generatedFrom(weightedGeneratedEndpoint)
                                                .legacy(false)
                                                .authMethod(AuthMethod.none)
                                                .in(controller.system()));
                }
            }
        }
        return filterEndpoints(deployment.applicationId(), EndpointList.copyOf(endpoints));
    }

    /** Read routing policies and return zone- and region-scoped endpoints for given deployment */
    public EndpointList readEndpointsOf(DeploymentId deployment) {
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        for (var policy : routingPolicies.read(deployment)) {
            endpoints.addAll(endpointsOf(deployment, policy.id().cluster(), policy.generatedEndpoints().cluster()).asList());
        }
        return EndpointList.copyOf(endpoints);
    }

    // -------------- Declared endpoints (scopes 'global' and 'application') --------------

    /** Returns global endpoints pointing to given deployments */
    public EndpointList declaredEndpointsOf(RoutingId routingId, ClusterSpec.Id cluster, List<DeploymentId> deployments, GeneratedEndpointList generatedEndpoints) {
        requireGeneratedEndpoints(generatedEndpoints, true);
        var endpoints = new ArrayList<Endpoint>();
        var directMethods = 0;
        var availableRoutingMethods = routingMethodsOfAll(deployments);
        boolean generatedEndpointsAvailable = !generatedEndpoints.isEmpty();
        for (var method : availableRoutingMethods) {
            if (method.isDirect() && ++directMethods > 1) {
                throw new IllegalArgumentException("Invalid routing methods for " + routingId + ": Exceeded maximum " +
                                                   "direct methods");
            }
            Endpoint.EndpointBuilder builder = Endpoint.of(routingId.instance())
                                                       .target(routingId.endpointId(), cluster, deployments)
                                                       .on(Port.fromRoutingMethod(method))
                                                       .legacy(generatedEndpointsAvailable)
                                                       .routingMethod(method);
            endpoints.add(builder.in(controller.system()));
            for (var ge : generatedEndpoints) {
                endpoints.add(builder.generatedFrom(ge).legacy(false).authMethod(ge.authMethod()).in(controller.system()));
            }
        }
        return filterEndpoints(routingId.instance(), EndpointList.copyOf(endpoints));
    }

    /** Returns application endpoints pointing to given deployments */
    public EndpointList declaredEndpointsOf(TenantAndApplicationId application, EndpointId endpoint, ClusterSpec.Id cluster,
                                            Map<DeploymentId, Integer> deployments, GeneratedEndpointList generatedEndpoints) {
        requireGeneratedEndpoints(generatedEndpoints, true);
        ZoneId zone = deployments.keySet().iterator().next().zoneId(); // Where multiple zones are possible, they all have the same routing method.
        RoutingMethod routingMethod = usesSharedRouting(zone) ? RoutingMethod.sharedLayer4 : RoutingMethod.exclusive;
        boolean generatedEndpointsAvailable = !generatedEndpoints.isEmpty();
        Endpoint.EndpointBuilder builder = Endpoint.of(application)
                                                   .targetApplication(endpoint,
                                                                      cluster,
                                                                      deployments)
                                                   .routingMethod(routingMethod)
                                                   .legacy(generatedEndpointsAvailable)
                                                   .on(Port.fromRoutingMethod(routingMethod));
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(builder.in(controller.system()));
        for (var ge : generatedEndpoints) {
            endpoints.add(builder.generatedFrom(ge).legacy(false).authMethod(ge.authMethod()).in(controller.system()));
        }
        return EndpointList.copyOf(endpoints);
    }

    /** Read application and return endpoints for all instances in application */
    public EndpointList readDeclaredEndpointsOf(Application application) {
        return declaredEndpointsOf(application.id(), application.deploymentSpec(), readDeclaredGeneratedEndpoints(application.id()));
    }

    /** Read application and return declared endpoints for given instance */
    public EndpointList readDeclaredEndpointsOf(ApplicationId instance) {
        if (SystemApplication.matching(instance).isPresent()) return EndpointList.EMPTY;
        Application application = controller.applications().requireApplication(TenantAndApplicationId.from(instance));
        return readDeclaredEndpointsOf(application).instance(instance.instance());
    }

    private EndpointList declaredEndpointsOf(TenantAndApplicationId application, DeploymentSpec deploymentSpec, Map<EndpointId, GeneratedEndpointList> generatedEndpoints) {
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        // Global endpoints
        for (var spec : deploymentSpec.instances()) {
            ApplicationId instance = application.instance(spec.name());
            for (var declaredEndpoint : spec.endpoints()) {
                RoutingId routingId = RoutingId.of(instance, EndpointId.of(declaredEndpoint.endpointId()));
                List<DeploymentId> deployments = declaredEndpoint.regions().stream()
                                                                 .map(region -> new DeploymentId(instance,
                                                                                                 ZoneId.from(Environment.prod, region)))
                                                                 .toList();
                ClusterSpec.Id cluster = ClusterSpec.Id.from(declaredEndpoint.containerId());
                GeneratedEndpointList generatedForId = generatedEndpoints.getOrDefault(routingId.endpointId(), GeneratedEndpointList.EMPTY);
                endpoints.addAll(declaredEndpointsOf(routingId, cluster, deployments, generatedForId).asList());
            }
        }
        // Application endpoints
        for (var declaredEndpoint : deploymentSpec.endpoints()) {
            Map<DeploymentId, Integer> deployments = declaredEndpoint.targets().stream()
                                                                     .collect(toMap(t -> new DeploymentId(application.instance(t.instance()),
                                                                                                          ZoneId.from(Environment.prod, t.region())),
                                                                                    t -> t.weight()));
            ClusterSpec.Id cluster = ClusterSpec.Id.from(declaredEndpoint.containerId());
            EndpointId endpointId = EndpointId.of(declaredEndpoint.endpointId());
            GeneratedEndpointList generatedForId = generatedEndpoints.getOrDefault(endpointId, GeneratedEndpointList.EMPTY);
            endpoints.addAll(declaredEndpointsOf(application, endpointId, cluster, deployments, generatedForId).asList());
        }
        return EndpointList.copyOf(endpoints);
    }

    // -------------- Other gunk related to endpoints and routing --------------

    /** Read endpoints for use in deployment steps, for given deployments, grouped by their zone */
    public Map<ZoneId, List<Endpoint>> readStepRunnerEndpointsOf(Collection<DeploymentId> deployments) {
        TreeMap<ZoneId, List<Endpoint>> endpoints = new TreeMap<>(Comparator.comparing(ZoneId::value));
        for (var deployment : deployments) {
            EndpointList zoneEndpoints = readEndpointsOf(deployment).scope(Endpoint.Scope.zone)
                                                                    .authMethod(AuthMethod.mtls)
                                                                    .not().legacy();
            EndpointList directEndpoints = zoneEndpoints.direct();
            if (!directEndpoints.isEmpty()) {
                zoneEndpoints = directEndpoints; // Use only direct endpoints if we have any
            }
            EndpointList generatedEndpoints = zoneEndpoints.generated();
            if (!generatedEndpoints.isEmpty()) {
                zoneEndpoints = generatedEndpoints; // Use generated endpoints if we have any
            }
            if  ( ! zoneEndpoints.isEmpty()) {
                endpoints.put(deployment.zoneId(), zoneEndpoints.asList());
            }
        }
        return Collections.unmodifiableSortedMap(endpoints);
    }

    /** Returns certificate DNS names (CN and SAN values) for given deployment */
    public List<String> certificateDnsNames(DeploymentId deployment, DeploymentSpec deploymentSpec, String generatedId, boolean legacy) {
        List<String> endpointDnsNames = new ArrayList<>();
        if (legacy) {
            endpointDnsNames.addAll(legacyCertificateDnsNames(deployment, deploymentSpec));
        }
        for (Scope scope : List.of(Scope.zone, Scope.global, Scope.application)) {
            endpointDnsNames.add(Endpoint.of(deployment.applicationId())
                                         .wildcardGenerated(generatedId, scope)
                                         .routingMethod(RoutingMethod.exclusive)
                                         .on(Port.tls())
                                         .certificateName()
                                         .in(controller.system())
                                         .dnsName());
        }
        return Collections.unmodifiableList(endpointDnsNames);
    }

    private List<String> legacyCertificateDnsNames(DeploymentId deployment, DeploymentSpec deploymentSpec) {
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

    /** Remove endpoints in DNS for all rotations assigned to given instance */
    public void removeRotationEndpointsFromDns(Application application, InstanceName instanceName) {
        Set<Endpoint> endpointsToRemove = new LinkedHashSet<>();
        Instance instance = application.require(instanceName);
        // Compute endpoints from rotations. When removing DNS records for rotation-based endpoints we cannot use the
        // deployment spec, because submitting an empty deployment spec is the first step of removing an application
        for (var rotation : instance.rotations()) {
            var deployments = rotation.regions().stream()
                                      .map(region -> new DeploymentId(instance.id(), ZoneId.from(Environment.prod, region)))
                                      .toList();
            GeneratedEndpointList generatedForId = readDeclaredGeneratedEndpoints(application.id()).getOrDefault(rotation.endpointId(), GeneratedEndpointList.EMPTY);
            endpointsToRemove.addAll(declaredEndpointsOf(RoutingId.of(instance.id(), rotation.endpointId()),
                                                         rotation.clusterId(), deployments,
                                                         generatedForId)
                                             .asList());
        }
        endpointsToRemove.forEach(endpoint -> controller.nameServiceForwarder()
                                                        .removeRecords(Record.Type.CNAME,
                                                                       RecordName.from(endpoint.dnsName()),
                                                                       Priority.normal,
                                                                       Optional.of(application.id())));
    }

    private EndpointList filterEndpoints(ApplicationId instance, EndpointList endpoints) {
        return endpointConfig(instance) == EndpointConfig.generated ? endpoints.generated() : endpoints;
    }

    private void registerRotationEndpointsInDns(PreparedEndpoints prepared) {
        TenantAndApplicationId owner = TenantAndApplicationId.from(prepared.deployment().applicationId());
        EndpointList globalEndpoints = prepared.endpoints().scope(Scope.global);
        for (var assignedRotation : prepared.rotations()) {
            EndpointList rotationEndpoints = globalEndpoints.named(assignedRotation.endpointId(), Scope.global)
                                                            .requiresRotation();
            // Skip rotations which do not apply to this zone
            if (!assignedRotation.regions().contains(prepared.deployment().zoneId().region())) {
                continue;
            }
            // Register names in DNS
            Rotation rotation = rotationRepository.requireRotation(assignedRotation.rotationId());
            for (var endpoint : rotationEndpoints) {
                controller.nameServiceForwarder().createRecord(
                        new Record(Record.Type.CNAME, RecordName.from(endpoint.dnsName()), RecordData.fqdn(rotation.name())),
                        Priority.normal,
                        Optional.of(owner)
                );
            }
        }
        for (var endpoint : prepared.endpoints().scope(Scope.application).shared()) { // DNS for non-shared application endpoints is handled by RoutingPolicies
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
                    Optional.of(owner));
        }
    }

    /** Returns generated endpoints. A new endpoint is generated if no matching endpoint already exists */
    private List<GeneratedEndpoint> generateEndpoints(AuthMethod authMethod, EndpointCertificate certificate,
                                                      Optional<EndpointId> declaredEndpoint,
                                                      List<GeneratedEndpoint> current) {
        if (current.stream().anyMatch(e -> e.authMethod() == authMethod && e.endpoint().equals(declaredEndpoint))) {
            return current;
        }
        Optional<String> applicationPart = certificate.generatedId();
        if (applicationPart.isPresent()) {
            current = new ArrayList<>(current);
            current.add(new GeneratedEndpoint(GeneratedEndpoint.createPart(controller.random(true)),
                                              applicationPart.get(),
                                              authMethod,
                                              declaredEndpoint));
        }
        return current;
    }

    /** Generate the  cluster part of a {@link GeneratedEndpoint} for use in a {@link Endpoint.Scope#weighted} endpoint */
    private String weightedClusterPart(ClusterSpec.Id cluster, DeploymentId deployment) {
        // This ID must be common for a given cluster in all deployments within the same cloud-native region
        String cloudNativeRegion = controller.zoneRegistry().zones().all().get(deployment.zoneId()).get().getCloudNativeRegionName();
        HashCode hash = Hashing.sha256().newHasher()
                               .putString(cluster.value(), StandardCharsets.UTF_8)
                               .putString(":", StandardCharsets.UTF_8)
                               .putString(cloudNativeRegion, StandardCharsets.UTF_8)
                               .putString(":", StandardCharsets.UTF_8)
                               .putString(deployment.applicationId().serializedForm(), StandardCharsets.UTF_8)
                               .hash();
        String alphabet = "abcdef";
        char letter = alphabet.charAt(Math.abs(hash.asInt()) % alphabet.length());
        return letter + hash.toString().substring(0, 7);
    }

    /** Returns existing generated endpoints, grouped by their {@link Scope#multiDeployment()} endpoint */
    private Map<EndpointId, GeneratedEndpointList> readDeclaredGeneratedEndpoints(TenantAndApplicationId application) {
        Map<EndpointId, GeneratedEndpointList> endpoints = new HashMap<>();
        for (var policy : policies().read(application)) {
            Map<EndpointId, GeneratedEndpointList> generatedForDeclared = policy.generatedEndpoints()
                                                                                .not().cluster()
                                                                                .groupingBy(ge -> ge.endpoint().get());
            generatedForDeclared.forEach(endpoints::putIfAbsent);
        }
        return endpoints;
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

    private static void requireGeneratedEndpoints(GeneratedEndpointList generatedEndpoints, boolean declared) {
        if (generatedEndpoints.asList().stream().anyMatch(ge -> ge.declared() != declared)) {
            throw new IllegalStateException("All generated endpoints require declared=" + declared +
                                            ", got " + generatedEndpoints);
        }
    }

    /** Create a common name based on a hash of given application. This must be less than 64 characters long. */
    private static String commonNameHashOf(ApplicationId application, SystemName system) {
        @SuppressWarnings("deprecation") // for Hashing.sha1()
        HashCode sha1 = Hashing.sha1().hashString(application.serializedForm(), StandardCharsets.UTF_8);
        String base32 = BaseEncoding.base32().omitPadding().lowerCase().encode(sha1.asBytes());
        return 'v' + base32 + Endpoint.internalDnsSuffix(system);
    }

}
