// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentSteps;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This contains validators for a {@link ApplicationPackage} that depend on a {@link Controller} to perform validation.
 *
 * @author mpolden
 */
public class ApplicationPackageValidator {

    private final Controller controller;
    private final ListFlag<String> cloudAccountsFlag;

    public ApplicationPackageValidator(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.cloudAccountsFlag = PermanentFlags.CLOUD_ACCOUNTS.bindTo(controller.flagSource());
    }

    /**
     * Validate the given application package
     *
     * @throws IllegalArgumentException if any validations fail
     */
    public void validate(Application application, ApplicationPackage applicationPackage, Instant instant) {
        validateSteps(applicationPackage.deploymentSpec());
        validateCloudAccounts(application, applicationPackage.deploymentSpec());
        validateEndpointRegions(applicationPackage.deploymentSpec());
        validateEndpointChange(application, applicationPackage, instant);
        validateCompactedEndpoint(applicationPackage);
        validateSecurityClientsPem(applicationPackage);
    }

    /** Verify that we have the security/clients.pem file for public systems */
    private void validateSecurityClientsPem(ApplicationPackage applicationPackage) {
        if (!controller.system().isPublic() || applicationPackage.deploymentSpec().steps().isEmpty()) return;
        if (applicationPackage.trustedCertificates().isEmpty())
            throw new IllegalArgumentException("Missing required file 'security/clients.pem'");
    }

    /** Verify that each of the production zones listed in the deployment spec exist in this system */
    private void validateSteps(DeploymentSpec deploymentSpec) {
        for (var spec : deploymentSpec.instances()) {
            new DeploymentSteps(spec, controller.zoneRegistry()).jobs();
            spec.zones().stream()
                .filter(zone -> zone.environment() == Environment.prod)
                .forEach(zone -> {
                    if ( ! controller.zoneRegistry().hasZone(ZoneId.from(zone.environment(),
                                                                         zone.region().orElseThrow()))) {
                        throw new IllegalArgumentException("Zone " + zone + " in deployment spec was not found in this system!");
                    }
                });
        }
    }

    /** Verify that no single endpoint contains regions in different clouds */
    private void validateEndpointRegions(DeploymentSpec deploymentSpec) {
        for (var instance : deploymentSpec.instances()) {
            for (var endpoint : instance.endpoints()) {
                var clouds = new HashSet<CloudName>();
                for (var region : endpoint.regions()) {
                    for (ZoneApi zone : controller.zoneRegistry().zones().all().in(Environment.prod).in(region).zones()) {
                        clouds.add(zone.getCloudName());
                    }
                }
                if (clouds.size() != 1) {
                    throw new IllegalArgumentException("Endpoint '" + endpoint.endpointId() + "' in " + instance +
                                                       " cannot contain regions in different clouds: " +
                                                       endpoint.regions().stream().sorted().collect(Collectors.toList()));
                }
            }
        }
    }

    /** Verify endpoint configuration of given application package */
    private void validateEndpointChange(Application application, ApplicationPackage applicationPackage, Instant instant) {
        applicationPackage.deploymentSpec().instances().forEach(instance -> validateEndpointChange(application,
                                                                                                   instance.name(),
                                                                                                   applicationPackage,
                                                                                                   instant));
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
    private void validateEndpointChange(Application application, InstanceName instanceName, ApplicationPackage applicationPackage, Instant instant) {
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

    /** Verify that declared cloud accounts are allowed to be used by the tenant */
    private void validateCloudAccounts(Application application, DeploymentSpec deploymentSpec) {
        TenantName tenant = application.id().tenant();
        Set<CloudAccount> validAccounts = cloudAccountsFlag.with(FetchVector.Dimension.TENANT_ID, tenant.value())
                                                           .value().stream()
                                                           .map(CloudAccount::new)
                                                           .collect(Collectors.toSet());
        for (var spec : deploymentSpec.instances()) {
            for (var zone : spec.zones()) {
                if (!zone.environment().isProduction()) continue;
                Optional<CloudAccount> cloudAccount = spec.cloudAccount(zone.environment(), zone.region().get());
                if (cloudAccount.isEmpty()) continue;
                if (validAccounts.contains(cloudAccount.get())) continue;
                throw new IllegalArgumentException("Cloud account '" + cloudAccount.get().value() +
                                                   "' is not valid for tenant '" + tenant + "'");
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
                                  .collect(Collectors.toList());
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
