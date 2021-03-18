// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgrader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Information about OS versions in this system.
 *
 * @author mpolden
 */
public class OsVersionStatus {

    public static final OsVersionStatus empty = new OsVersionStatus(ImmutableMap.of());

    private final Map<OsVersion, NodeVersions> versions;

    /** Public for serialization purpose only. Use {@link OsVersionStatus#compute(Controller)} for an up-to-date status */
    public OsVersionStatus(ImmutableMap<OsVersion, NodeVersions> versions) {
        this.versions = ImmutableMap.copyOf(Objects.requireNonNull(versions, "versions must be non-null"));
    }

    /** All known OS versions and their nodes */
    public Map<OsVersion, NodeVersions> versions() {
        return versions;
    }

    /** Returns nodes eligible for OS upgrades that exist in given cloud */
    public List<NodeVersion> nodesIn(CloudName cloud) {
        return versions.entrySet().stream()
                       .filter(entry -> entry.getKey().cloud().equals(cloud))
                       .flatMap(entry -> entry.getValue().asMap().values().stream())
                       .collect(Collectors.toUnmodifiableList());
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
        var osVersions = new HashMap<OsVersion, List<NodeVersion>>();
        controller.osVersionTargets().forEach(target -> osVersions.put(target.osVersion(), new ArrayList<>()));

        for (var application : SystemApplication.all()) {
            for (var zone : zonesToUpgrade(controller)) {
                if (!application.shouldUpgradeOsIn()) continue;
                var targetOsVersion = controller.serviceRegistry().configServer().nodeRepository()
                                                .targetVersionsOf(zone.getId())
                                                .osVersion(application.nodeType())
                                                .orElse(Version.emptyVersion);

                for (var node : controller.serviceRegistry().configServer().nodeRepository().list(zone.getId(), application.id())) {
                    if (!OsUpgrader.canUpgrade(node)) continue;
                    var suspendedAt = node.suspendedSince();
                    var nodeVersion = new NodeVersion(node.hostname(), zone.getId(), node.currentOsVersion(),
                                                      targetOsVersion, suspendedAt);
                    var osVersion = new OsVersion(nodeVersion.currentVersion(), zone.getCloudName());
                    osVersions.putIfAbsent(osVersion, new ArrayList<>());
                    osVersions.get(osVersion).add(nodeVersion);
                }
            }
        }

        var newOsVersions = ImmutableMap.<OsVersion, NodeVersions>builder();
        for (var osVersion : osVersions.entrySet()) {
            var nodeVersions = ImmutableMap.<HostName, NodeVersion>builder();
            for (var nodeVersion : osVersion.getValue()) {
                nodeVersions.put(nodeVersion.hostname(), nodeVersion);
            }
            newOsVersions.put(osVersion.getKey(), new NodeVersions(nodeVersions.build()));
        }
        return new OsVersionStatus(newOsVersions.build());
    }

    private static List<ZoneApi> zonesToUpgrade(Controller controller) {
        return controller.zoneRegistry().osUpgradePolicies().stream()
                         .flatMap(upgradePolicy -> upgradePolicy.asList().stream())
                         .flatMap(Collection::stream)
                         .collect(Collectors.toUnmodifiableList());
    }

}
