// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.zone;

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
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Provides information about zones in a hosted Vespa system, and about the system.
 *
 * @author mpolden
 */
public interface ZoneRegistry {

    /** Returns whether the system of this registry contains the given zone */
    boolean hasZone(ZoneId zoneId);

    /** Returns whether cloudAccount in this system supports given zone */
    boolean hasZone(ZoneId zoneId, CloudAccount cloudAccount);

    /** Returns whether the given cloud account is an enclave */
    boolean isEnclave(CloudAccount cloudAccount);

    /** Returns a list containing the id of all zones in this registry */
    ZoneFilter zones();

    /** Returns a list containing the id of all zones in this registry, including the system. */
    ZoneFilter zonesIncludingSystem();

    /** Returns the default region for the given environment, if one is configured */
    Optional<RegionName> getDefaultRegion(Environment environment);

    /** Throws {@link NoSuchElementException} if there is no such zone (in the system). */
    ZoneApi get(ZoneId zoneId);

    /** Returns the URI for the config server VIP in the given zone */
    URI getConfigServerVipUri(ZoneId zoneId);

    /** Returns the VIP hostname for the shared routing layer in given zone, if any */
    Optional<String> getVipHostname(ZoneId zoneId);

    /** Returns the time to live for deployments in the given zone, or empty if this is infinite */
    Optional<Duration> getDeploymentTimeToLive(ZoneId zoneId);

    /** Returns the monitoring system URL for the given deployment */
    URI getMonitoringSystemUri(DeploymentId deploymentId);

    /** Returns the system of this registry */
    SystemName system();

    /** Returns the system of this registry as a zone */
    ZoneApi systemZone();

    /** Return the configserver's Athenz service identity */
    AthenzIdentity getConfigServerHttpsIdentity(ZoneId zoneId);

    /** Return the Athenz service identity for a given node type */
    AthenzIdentity getNodeAthenzIdentity(ZoneId zoneId, NodeType nodeType);

    /**  Return the system Athenz domain */
    AthenzDomain accessControlDomain();

    /** Returns the Vespa upgrade policy to use for zones in this registry */
    UpgradePolicy upgradePolicy();

    /** Returns the OS upgrade policy to use for zones belonging to given cloud, in this registry */
    UpgradePolicy osUpgradePolicy(CloudName cloud);

    /** Returns all OS upgrade policies */
    List<UpgradePolicy> osUpgradePolicies();

    /** Returns the routing method used by given zone */
    RoutingMethod routingMethod(ZoneId zone);

    /** Returns a URL where an informative dashboard can be found. */
    URI dashboardUrl();

    /** Returns a URL which displays information about the given tenant. */
    URI dashboardUrl(TenantName id);

    /** Returns a URL which displays information about the given application. */
    URI dashboardUrl(TenantName tenantName, ApplicationName applicationName);

    /** Returns a URL which displays information about the given application instance. */
    URI dashboardUrl(ApplicationId id);

    /** Returns a URL which displays information about the given job run. */
    URI dashboardUrl(RunId id);

    /** Returns a URL used to request support from the Vespa team. */
    URI supportUrl();

    /** Returns a URL to the controller's api endpoint */
    URI apiUrl();

    /** IAM tenant developer role ARN */
    Optional<String> tenantDeveloperRoleArn(TenantName tenant);

    /** Returns athenz domain tied to the given cloud account, if any */
    Optional<AthenzDomain> cloudAccountAthenzDomain(CloudAccount cloudAccount);

}
