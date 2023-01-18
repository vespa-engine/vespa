// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredZone;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.application.api.Endpoint.Level;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

/**
 * This contains validators for a {@link ApplicationPackage} that depend on a {@link Controller} to perform validation.
 *
 * @author mpolden
 */
public class ApplicationPackageValidator {

    private final Controller controller;

    public ApplicationPackageValidator(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
    }

    /**
     * Validate the given application package
     *
     * @throws IllegalArgumentException if any validations fail
     */
    public void validate(Application application, ApplicationPackage applicationPackage, Instant instant) {
        validateSteps(applicationPackage.deploymentSpec());
        validateEndpointRegions(applicationPackage.deploymentSpec());
        validateEndpointChange(application, applicationPackage, instant);
        validateCompactedEndpoint(applicationPackage);
        validateDeprecatedElements(applicationPackage);
        validateCloudAccounts(application, applicationPackage);
    }

    private void validateCloudAccounts(Application application, ApplicationPackage applicationPackage) {
        Set<CloudAccount> tenantAccounts = new TreeSet<>(controller.applications().accountsOf(application.id().tenant()));
        Set<CloudAccount> declaredAccounts = new TreeSet<>();
        applicationPackage.deploymentSpec().cloudAccount().ifPresent(declaredAccounts::add);
        for (DeploymentInstanceSpec instance : applicationPackage.deploymentSpec().instances())
            for (ZoneId zone : controller.zoneRegistry().zones().controllerUpgraded().ids())
                instance.cloudAccount(zone.environment(), Optional.of(zone.region())).ifPresent(declaredAccounts::add);

        declaredAccounts.removeIf(tenantAccounts::contains);
        declaredAccounts.removeIf(CloudAccount::isUnspecified);
        if ( ! declaredAccounts.isEmpty())
            throw new IllegalArgumentException("cloud accounts " +
                                               declaredAccounts.stream().map(CloudAccount::value).collect(joining(", ", "[", "]")) +
                                               " are not valid for tenant " +
                                               application.id().tenant());
    }

    /** Verify that deployment spec does not use elements deprecated on a major version older than wanted major version */
    private void validateDeprecatedElements(ApplicationPackage applicationPackage) {
        int wantedMajor = applicationPackage.compileVersion().map(Version::getMajor)
                                            .or(() -> applicationPackage.deploymentSpec().majorVersion())
                                            .or(() -> controller.readVersionStatus().controllerVersion()
                                                                .map(VespaVersion::versionNumber)
                                                                .map(Version::getMajor))
                                            .orElseThrow(() -> new IllegalArgumentException("Could not determine wanted major version"));
        for (var deprecatedElement : applicationPackage.deploymentSpec().deprecatedElements()) {
            if (deprecatedElement.majorVersion() >= wantedMajor) continue;
            throw new IllegalArgumentException(deprecatedElement.humanReadableString());
        }
    }

    /** Verify that each of the production zones listed in the deployment spec exist in this system */
    private void validateSteps(DeploymentSpec deploymentSpec) {
        for (var spec : deploymentSpec.instances()) {
            for (var zone : spec.zones()) {
                Environment environment = zone.environment();
                if (zone.region().isEmpty()) continue;
                ZoneId zoneId = ZoneId.from(environment, zone.region().get());
                if (!controller.zoneRegistry().hasZone(zoneId)) {
                    throw new IllegalArgumentException("Zone " + zone + " in deployment spec was not found in this system!");
                }
            }
        }
    }

    /** Verify that:
     * <ul>
     *     <li>no single endpoint contains regions in different clouds</li>
     *     <li>application endpoints with different regions must be contained in CGP and AWS</li>
     * </ul>
     */
    private void validateEndpointRegions(DeploymentSpec deploymentSpec) {
        for (var instance : deploymentSpec.instances()) {
            validateEndpointRegions(instance.endpoints(), instance);
        }
        validateEndpointRegions(deploymentSpec.endpoints(), null);
    }

    private void validateEndpointRegions(List<Endpoint> endpoints, DeploymentInstanceSpec instance) {
        for (var endpoint : endpoints) {
            RegionName[] regions = new HashSet<>(endpoint.regions()).toArray(RegionName[]::new);
            Set<CloudName> clouds = controller.zoneRegistry().zones().all().in(Environment.prod)
                                              .in(regions)
                                              .zones().stream()
                                              .map(ZoneApi::getCloudName)
                                              .collect(Collectors.toSet());
            String endpointString = instance == null ? "Application endpoint '" + endpoint.endpointId() + "'"
                                                     : "Endpoint '" + endpoint.endpointId() + "' in " + instance;
            if (Set.of(CloudName.GCP, CloudName.AWS).containsAll(clouds)) { } // Everything is fine!
            else if (Set.of(CloudName.YAHOO).containsAll(clouds) || Set.of(CloudName.DEFAULT).containsAll(clouds)) {
                if (endpoint.level() == Level.application && regions.length != 1) {
                    throw new IllegalArgumentException(endpointString + " cannot contain different regions: " +
                                                       endpoint.regions().stream().sorted().toList());
                }
            }
            else if (clouds.size() == 1) {
                throw new IllegalArgumentException("unknown cloud '" + clouds.iterator().next() + "'");
            }
            else {
                throw new IllegalArgumentException(endpointString + " cannot contain regions in different clouds: " +
                                                   endpoint.regions().stream().sorted().toList());
            }
        }
    }

    /** Verify endpoint configuration of given application package */
    private void validateEndpointChange(Application application, ApplicationPackage applicationPackage, Instant instant) {
        for (DeploymentInstanceSpec instance : applicationPackage.deploymentSpec().instances()) {
            validateGlobalEndpointChanges(application, instance.name(), applicationPackage, instant);
            validateZoneEndpointChanges(application, instance.name(), applicationPackage, instant);
        }
    }

    /** Verify that compactable endpoint parts (instance name and endpoint ID) do not clash */
    private void validateCompactedEndpoint(ApplicationPackage applicationPackage) {
        Map<List<String>, InstanceEndpoint> instanceEndpoints = new HashMap<>();
        for (var instanceSpec : applicationPackage.deploymentSpec().instances()) {
            for (var endpoint : instanceSpec.endpoints()) {
                List<String> nonCompactableIds = nonCompactableIds(instanceSpec.name(), endpoint);
                InstanceEndpoint instanceEndpoint = new InstanceEndpoint(instanceSpec.name(), endpoint.endpointId());
                InstanceEndpoint existingEndpoint = instanceEndpoints.get(nonCompactableIds);
                if (existingEndpoint != null) {
                    throw new IllegalArgumentException("Endpoint with ID '" + endpoint.endpointId() + "' in instance '"
                                                       + instanceSpec.name().value() +
                                                       "' clashes with endpoint '" + existingEndpoint.endpointId +
                                                       "' in instance '" + existingEndpoint.instance + "'");
                }
                instanceEndpoints.put(nonCompactableIds, instanceEndpoint);
            }
        }
    }

    /** Verify changes to endpoint configuration by comparing given application package to the existing one, if any */
    private void validateGlobalEndpointChanges(Application application, InstanceName instanceName, ApplicationPackage applicationPackage, Instant instant) {
        var validationId = ValidationId.globalEndpointChange;
        if (applicationPackage.validationOverrides().allows(validationId, instant)) return;

        var endpoints = application.deploymentSpec().instance(instanceName)
                                   .map(ApplicationPackageValidator::allEndpointsOf)
                                   .orElseGet(List::of);
        var newEndpoints = allEndpointsOf(applicationPackage.deploymentSpec().requireInstance(instanceName));

        if (newEndpoints.containsAll(endpoints)) return; // Adding new endpoints is fine
        if (containsAllDestinationsOf(endpoints, newEndpoints)) return; // Adding destinations is fine

        var removedEndpoints = new ArrayList<>(endpoints);
        removedEndpoints.removeAll(newEndpoints);
        newEndpoints.removeAll(endpoints);
        throw new IllegalArgumentException(validationId.value() + ": application '" + application.id() +
                                           (instanceName.isDefault() ? "" : "." + instanceName.value()) +
                                           "' has endpoints " + endpoints +
                                           ", but does not include all of these in deployment.xml. Deploying given " +
                                           "deployment.xml will remove " + removedEndpoints +
                                           (newEndpoints.isEmpty() ? "" : " and add " + newEndpoints) +
                                           ". " + ValidationOverrides.toAllowMessage(validationId));
    }

    /** Verify changes to endpoint configuration by comparing given application package to the existing one, if any */
    private void validateZoneEndpointChanges(Application application, InstanceName instance, ApplicationPackage applicationPackage, Instant now) {
        ValidationId validationId = ValidationId.zoneEndpointChange;
        if (applicationPackage.validationOverrides().allows(validationId, now)) return;;

        String prefix = validationId + ": application '" + application.id() +
                        (instance.isDefault() ? "" : "." + instance.value()) + "' ";
        DeploymentInstanceSpec spec = applicationPackage.deploymentSpec().requireInstance(instance);
        for (DeclaredZone zone : spec.zones()) {
            if (zone.environment() == Environment.prod) {
                Map<ClusterSpec.Id, ZoneEndpoint> newEndpoints = spec.zoneEndpoints(ZoneId.from(zone.environment(), zone.region().get()));
                application.deploymentSpec().instance(instance)                                     // If old spec has this instance ...
                           .filter(oldSpec -> oldSpec.concerns(zone.environment(), zone.region()))  // ... and deploys to this zone ...
                           .map(oldSpec -> oldSpec.zoneEndpoints(ZoneId.from(zone.environment(), zone.region().get())))
                           .ifPresent(oldEndpoints -> {                                             // ... then we compare the endpoints present in both.
                               oldEndpoints.forEach((cluster, oldEndpoint) -> {
                                   ZoneEndpoint newEndpoint = newEndpoints.getOrDefault(cluster, ZoneEndpoint.defaultEndpoint);
                                   if ( ! newEndpoint.allowedUrns().containsAll(oldEndpoint.allowedUrns()))
                                       throw new IllegalArgumentException(prefix + "allows access to cluster '" + cluster.value() +
                                                                          "' in '" + zone.region().get().value() + "' to " +
                                                                          oldEndpoint.allowedUrns().stream().map(AllowedUrn::toString).collect(joining(", ", "[", "]")) +
                                                                          ", but does not include all these in the new deployment spec. " +
                                                                          "Deploying with the new settings will allow access to " +
                                                                          (newEndpoint.allowedUrns().isEmpty() ? "no one" : newEndpoint.allowedUrns().stream().map(AllowedUrn::toString).collect(joining(", ", "[", "]"))));
                               });
                               newEndpoints.forEach((cluster, newEndpoint) -> {
                                    ZoneEndpoint oldEndpoint = oldEndpoints.getOrDefault(cluster, ZoneEndpoint.defaultEndpoint);
                                    if (oldEndpoint.isPublicEndpoint() && ! newEndpoint.isPublicEndpoint())
                                        throw new IllegalArgumentException(prefix + "has a public endpoint for cluster '" + cluster.value() +
                                                                           "' in '" + zone.region().get().value() + "', but the new deployment spec " +
                                                                           "disables this");
                               });
                           });
            }
        }
    }

    /** Returns whether newEndpoints contains all destinations in endpoints */
    private static boolean containsAllDestinationsOf(List<Endpoint> endpoints, List<Endpoint> newEndpoints) {
        var containsAllRegions = true;
        var hasSameCluster = true;
        for (var endpoint : endpoints) {
            var endpointContainsAllRegions = false;
            var endpointHasSameCluster = false;
            for (var newEndpoint : newEndpoints) {
                if (endpoint.endpointId().equals(newEndpoint.endpointId())) {
                    endpointContainsAllRegions = newEndpoint.regions().containsAll(endpoint.regions());
                    endpointHasSameCluster = newEndpoint.containerId().equals(endpoint.containerId());
                }
            }
            containsAllRegions &= endpointContainsAllRegions;
            hasSameCluster &= endpointHasSameCluster;
        }
        return containsAllRegions && hasSameCluster;
    }

    /** Returns all configued endpoints of given deployment instance spec */
    private static List<Endpoint> allEndpointsOf(DeploymentInstanceSpec deploymentInstanceSpec) {
        var endpoints = new ArrayList<>(deploymentInstanceSpec.endpoints());
        legacyEndpoint(deploymentInstanceSpec).ifPresent(endpoints::add);
        return endpoints;
    }

    /** Returns global service ID as an endpoint, if any global service ID is set */
    private static Optional<Endpoint> legacyEndpoint(DeploymentInstanceSpec instance) {
        return instance.globalServiceId().map(globalServiceId -> {
            var targets = instance.zones().stream()
                                  .filter(zone -> zone.environment().isProduction())
                                  .flatMap(zone -> zone.region().stream())
                                  .distinct()
                                  .map(region -> new Endpoint.Target(region, instance.name(), 1))
                                  .toList();
            return new Endpoint(EndpointId.defaultId().id(), globalServiceId, Endpoint.Level.instance, targets);
        });
    }

    /** Returns a list of the non-compactable IDs of given instance and endpoint */
    private static List<String> nonCompactableIds(InstanceName instance, Endpoint endpoint) {
        List<String> ids = new ArrayList<>(2);
        if (!instance.isDefault()) {
            ids.add(instance.value());
        }
        if (!"default".equals(endpoint.endpointId())) {
            ids.add(endpoint.endpointId());
        }
        return ids;
    }

    private static class InstanceEndpoint {

        private final InstanceName instance;
        private final String endpointId;

        public InstanceEndpoint(InstanceName instance, String endpointId) {
            this.instance = instance;
            this.endpointId = endpointId;
        }

    }

}
