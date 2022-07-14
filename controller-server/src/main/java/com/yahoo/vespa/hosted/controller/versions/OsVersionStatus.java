// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgrader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Information about OS versions in this system.
 *
 * @author mpolden
 */
public record OsVersionStatus(Map<OsVersion, List<NodeVersion>> versions) {

    public static final OsVersionStatus empty = new OsVersionStatus(ImmutableMap.of());

    /** Public for serialization purpose only. Use {@link OsVersionStatus#compute(Controller)} for an up-to-date status */
    public OsVersionStatus(Map<OsVersion, List<NodeVersion>> versions) {
        this.versions = ImmutableMap.copyOf(Objects.requireNonNull(versions, "versions must be non-null"));
    }

    /** Returns nodes eligible for OS upgrades that exist in given cloud */
    public List<NodeVersion> nodesIn(CloudName cloud) {
        return versions.entrySet().stream()
                       .filter(entry -> entry.getKey().cloud().equals(cloud))
                       .map(Map.Entry::getValue)
                       .findFirst()
                       .orElseGet(List::of);
    }

    /** Returns versions that exist in given cloud */
    public Set<Version> versionsIn(CloudName cloud) {
        return versions.keySet().stream()
                       .filter(osVersion -> osVersion.cloud().equals(cloud))
                       .map(OsVersion::version)
                       .collect(Collectors.toUnmodifiableSet());
    }

    /** Compute the current OS versions in this system. This is expensive and should be called infrequently */
    public static OsVersionStatus compute(Controller controller) {
        Map<OsVersion, List<NodeVersion>> osVersions = new HashMap<>();
        controller.osVersionTargets().forEach(target -> osVersions.put(target.osVersion(), new ArrayList<>()));

        for (var application : SystemApplication.all()) {
            for (var zone : zonesToUpgrade(controller)) {
                if (!application.shouldUpgradeOs()) continue;
                Version targetOsVersion = controller.serviceRegistry().configServer().nodeRepository()
                                                    .targetVersionsOf(zone.getVirtualId())
                                                    .osVersion(application.nodeType())
                                                    .orElse(Version.emptyVersion);

                for (var node : controller.serviceRegistry().configServer().nodeRepository().list(zone.getVirtualId(), NodeFilter.all().applications(application.id()))) {
                    if (!OsUpgrader.canUpgrade(node, true)) continue;
                    Optional<Instant> suspendedAt = node.suspendedSince();
                    NodeVersion nodeVersion = new NodeVersion(node.hostname(), zone.getVirtualId(), node.currentOsVersion(),
                                                              targetOsVersion, suspendedAt);
                    OsVersion osVersion = new OsVersion(nodeVersion.currentVersion(), zone.getCloudName());
                    osVersions.computeIfAbsent(osVersion, (k) -> new ArrayList<>())
                              .add(nodeVersion);
                }
            }
        }

        return new OsVersionStatus(osVersions);
    }

    private static List<ZoneApi> zonesToUpgrade(Controller controller) {
        return controller.zoneRegistry().osUpgradePolicies().stream()
                         .flatMap(upgradePolicy -> upgradePolicy.steps().stream())
                         .map(UpgradePolicy.Step::zones)
                         .flatMap(Collection::stream)
                         .toList();
    }

}
