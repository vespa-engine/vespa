// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneFilter;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author mpolden
 */
public class ZoneRegistryMock extends AbstractComponent implements ZoneRegistry {

    private final Map<ZoneId, Duration> deploymentTimeToLive = new HashMap<>();
    private final Map<Environment, RegionName> defaultRegionForEnvironment = new HashMap<>();
    private final Map<CloudName, UpgradePolicy> osUpgradePolicies = new HashMap<>();
    private final Map<ZoneApi, RoutingMethod> zoneRoutingMethods = new HashMap<>();
    private final Map<CloudAccount, Set<ZoneId>> cloudAccountZones = new HashMap<>();
    private final Set<ZoneApi> dynamicallyProvisioned = new HashSet<>();
    private final SystemName system; // Don't even think about making it non-final!   ƪ(`▿▿▿▿´ƪ)

    private List<? extends ZoneApi> zones;
    private CloudAccount systemCloudAccount = CloudAccount.from("111333555777");
    private UpgradePolicy upgradePolicy = null;

    /**
     * This sets the default list of zones contained in this. If your test need a particular set of zones, use
     * {@link #setZones(List)}  instead of changing the default set.
     */
    public ZoneRegistryMock(SystemName system) {
        this.system = system;
        if (system.isPublic()) {
            this.zones = List.of(ZoneApiMock.fromId("test.us-east-1"),
                                 ZoneApiMock.fromId("staging.us-east-3"),
                                 ZoneApiMock.newBuilder().withId("prod.aws-us-east-1c").withCloud("aws").withCloudNativeAvailabilityZone("use1-az2").build(),
                                 ZoneApiMock.newBuilder().withId("prod.aws-eu-west-1a").withCloud("aws").withCloudNativeAvailabilityZone("euw1-az3").build(),
                                 ZoneApiMock.newBuilder().withId("dev.aws-us-east-1c").withCloud("aws").withCloudNativeAvailabilityZone("use1-az2").build());
                    setRoutingMethod(this.zones, RoutingMethod.exclusive);
        } else {
            this.zones = List.of(ZoneApiMock.fromId("test.us-east-1"),
                                 ZoneApiMock.fromId("staging.us-east-3"),
                                 ZoneApiMock.fromId("dev.us-east-1"),
                                 ZoneApiMock.newBuilder().withId("dev.aws-us-east-2a").withCloud("aws").build(),
                                 ZoneApiMock.fromId("perf.us-east-3"),
                                 ZoneApiMock.newBuilder().withId("prod.aws-us-east-1a").withCloud("aws").build(),
                                 ZoneApiMock.newBuilder().withId("prod.aws-us-east-1b").withCloud("aws").build(),
                                 ZoneApiMock.fromId("prod.ap-northeast-1"),
                                 ZoneApiMock.fromId("prod.ap-northeast-2"),
                                 ZoneApiMock.fromId("prod.ap-southeast-1"),
                                 ZoneApiMock.fromId("prod.us-east-3"),
                                 ZoneApiMock.fromId("prod.us-west-1"),
                                 ZoneApiMock.fromId("prod.us-central-1"),
                                 ZoneApiMock.fromId("prod.eu-west-1"));
            for (ZoneApi zone : this.zones)
                setRoutingMethod(zone, zone.getCloudName().equals(CloudName.DEFAULT) ? RoutingMethod.sharedLayer4 : RoutingMethod.exclusive);
        }
    }

    public ZoneRegistryMock setDeploymentTimeToLive(ZoneId zone, Duration duration) {
        deploymentTimeToLive.put(zone, duration);
        return this;
    }

    public ZoneRegistryMock setDefaultRegionForEnvironment(Environment environment, RegionName region) {
        defaultRegionForEnvironment.put(environment, region);
        return this;
    }

    public ZoneRegistryMock setZones(List<? extends ZoneApi> zones) {
        this.zones = zones;
        return this;
    }

    public ZoneRegistryMock setZones(ZoneApi... zone) {
        return setZones(List.of(zone));
    }

    public ZoneRegistryMock addZones(ZoneApi... zones) {
        List<ZoneApi> allZones = new ArrayList<>(this.zones);
        Collections.addAll(allZones, zones);
        return setZones(allZones);
    }

    public ZoneRegistryMock setUpgradePolicy(UpgradePolicy upgradePolicy) {
        this.upgradePolicy = upgradePolicy;
        return this;
    }

    public ZoneRegistryMock setOsUpgradePolicy(CloudName cloud, UpgradePolicy upgradePolicy) {
        osUpgradePolicies.put(cloud, upgradePolicy);
        return this;
    }

    public ZoneRegistryMock exclusiveRoutingIn(ZoneApi... zones) {
        return exclusiveRoutingIn(List.of(zones));
    }

    public ZoneRegistryMock exclusiveRoutingIn(List<? extends ZoneApi> zones) {
        return setRoutingMethod(zones, RoutingMethod.exclusive);
    }

    public ZoneRegistryMock setRoutingMethod(List<? extends ZoneApi> zones, RoutingMethod routingMethod) {
        zones.forEach(zone -> setRoutingMethod(zone, routingMethod));
        return this;
    }

    public ZoneRegistryMock setRoutingMethod(ZoneApi zone, RoutingMethod routingMethod) {
        this.zoneRoutingMethods.put(zone, routingMethod);
        return this;
    }

    public ZoneRegistryMock dynamicProvisioningIn(ZoneApi... zones) {
        return dynamicProvisioningIn(List.of(zones));
    }

    public ZoneRegistryMock dynamicProvisioningIn(List<ZoneApi> zones) {
        this.dynamicallyProvisioned.addAll(zones);
        return this;
    }

    public ZoneRegistryMock configureCloudAccount(CloudAccount cloudAccount, ZoneId... zones) {
        this.cloudAccountZones.computeIfAbsent(cloudAccount, (k) -> new HashSet<>()).addAll(Set.of(zones));
        return this;
    }

    @Override
    public SystemName system() {
        return system;
    }

    @Override
    public ZoneApi systemZone() {
        return ZoneApiMock.fromId("prod.controller");
    }

    @Override
    public ZoneFilter zones() {
        return ZoneFilterMock.from(zones, zoneRoutingMethods, dynamicallyProvisioned);
    }

    @Override
    public ZoneFilter zonesIncludingSystem() {
        var fullZones = new ArrayList<ZoneApi>(1 + zones.size());
        fullZones.add(systemAsZone());
        fullZones.addAll(zones);
        return ZoneFilterMock.from(fullZones, zoneRoutingMethods, dynamicallyProvisioned);
    }

    private ZoneApiMock systemAsZone() {
        return ZoneApiMock.newBuilder()
                          .with(ZoneId.from("prod.us-east-1"))
                          .withVirtualId(ZoneId.from("prod.controller"))
                          .build();
    }

    @Override
    public AthenzService getConfigServerHttpsIdentity(ZoneId zone) {
        return new AthenzService("vespadomain", "provider-" + zone.environment().value() + "-" + zone.region().value());
    }

    @Override
    public AthenzIdentity getNodeAthenzIdentity(ZoneId zoneId, NodeType nodeType) {
        return new AthenzService("vespadomain", "servicename");
    }

    @Override
    public AthenzDomain accessControlDomain() {
        return AthenzDomain.from("vespadomain");
    }

    @Override
    public UpgradePolicy upgradePolicy() {
        return upgradePolicy;
    }

    @Override
    public UpgradePolicy osUpgradePolicy(CloudName cloud) {
        return osUpgradePolicies.get(cloud);
    }

    @Override
    public List<UpgradePolicy> osUpgradePolicies() {
        return List.copyOf(osUpgradePolicies.values());
    }

    @Override
    public RoutingMethod routingMethod(ZoneId zone) {
        return Objects.requireNonNull(zoneRoutingMethods.get(ZoneApiMock.from(zone)));
    }

    @Override
    public URI dashboardUrl() {
        return URI.create("https://dashboard.tld");
    }

    @Override
    public URI dashboardUrl(ApplicationId id) {
        return URI.create("https://dashboard.tld/" + id);
    }

    @Override
    public URI dashboardUrl(TenantName tenantName, ApplicationName applicationName) {
        return URI.create("https://dashboard.tld/" + tenantName + "/" + applicationName);
    }

    @Override
    public URI dashboardUrl(TenantName tenantName) {
        return URI.create("https://dashboard.tld/" + tenantName);
    }

    @Override
    public URI dashboardUrl(RunId id) {
        return URI.create("https://dashboard.tld/" + id.application() + "/" + id.type().jobName() + "/" + id.number());
    }

    @Override
    public URI supportUrl() {
        return URI.create("https://help.tld");
    }

    @Override
    public URI apiUrl() {
        return URI.create("https://api.tld:4443/");
    }

    @Override public Optional<String> tenantDeveloperRoleArn(TenantName tenant) { return Optional.empty(); }

    @Override
    public Optional<AthenzDomain> cloudAccountAthenzDomain(CloudAccount cloudAccount) {
        return Optional.of(AthenzDomain.from("vespa.enclave"));
    }

    @Override
    public boolean hasZone(ZoneId zoneId) {
        return zones.stream().anyMatch(zone -> zone.getId().equals(zoneId));
    }

    @Override
    public boolean hasZone(ZoneId zoneId, CloudAccount cloudAccount) {
        return hasZone(zoneId) && (system.isPublic() || cloudAccountZones.getOrDefault(cloudAccount, Set.of()).contains(zoneId));
    }

    @Override
    public boolean isExternal(CloudAccount cloudAccount) {
        return system.isPublic() && !cloudAccount.isUnspecified() && !cloudAccount.equals(systemCloudAccount);
    }

    @Override
    public URI getConfigServerVipUri(ZoneId zoneId) {
        return URI.create(Text.format("https://cfg.%s.test.vip:4443/", zoneId.value()));
    }

    @Override
    public Optional<String> getVipHostname(ZoneId zoneId) {
        if (routingMethod(zoneId).isShared()) {
            return Optional.of("vip." + zoneId.value());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Duration> getDeploymentTimeToLive(ZoneId zoneId) {
        return Optional.ofNullable(deploymentTimeToLive.get(zoneId));
    }

    @Override
    public Optional<RegionName> getDefaultRegion(Environment environment) {
        return Optional.ofNullable(defaultRegionForEnvironment.get(environment));
    }

    @Override
    public ZoneApi get(ZoneId zoneId) {
        return zones.stream()
                .filter(zone -> zone.getId().equals(zoneId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No zone with id '" + zoneId + "'"));
    }

    @Override
    public URI getMonitoringSystemUri(DeploymentId deploymentId) {
        return URI.create("http://monitoring-system.test/?environment=" + deploymentId.zoneId().environment().value() + "&region="
                          + deploymentId.zoneId().region().value() + "&application=" + deploymentId.applicationId().toShortString());
    }

}
