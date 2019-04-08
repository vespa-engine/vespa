// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgrader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    public static final OsVersionStatus empty = new OsVersionStatus(Collections.emptyMap());

    private final Map<OsVersion, List<Node>> versions;

    /** Public for serialization purpose only. Use {@link OsVersionStatus#compute(Controller)} for an up-to-date status */
    public OsVersionStatus(Map<OsVersion, List<Node>> versions) {
        this.versions = ImmutableMap.copyOf(Objects.requireNonNull(versions, "versions must be non-null"));
    }

    /** All known OS versions and their nodes */
    public Map<OsVersion, List<Node>> versions() {
        return versions;
    }

    /** Returns nodes eligible for OS upgrades that exist in given cloud */
    public List<Node> nodesIn(CloudName cloud) {
        return versions.entrySet().stream()
                       .filter(entry -> entry.getKey().cloud().equals(cloud))
                       .flatMap(entry -> entry.getValue().stream())
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
        Map<OsVersion, List<Node>> versions = new HashMap<>();

        // Always include all target versions
        controller.osVersions().forEach(osVersion -> versions.put(osVersion, new ArrayList<>()));

        for (SystemApplication application : SystemApplication.all()) {
            if (application.nodeTypesWithUpgradableOs().isEmpty()) {
                continue; // Avoid querying applications that do not contain nodes with upgradable OS
            }
            for (ZoneId zone : zonesToUpgrade(controller)) {
                controller.configServer().nodeRepository().list(zone, application.id()).stream()
                          .filter(node -> OsUpgrader.eligibleForUpgrade(node, application))
                          .map(node -> new Node(node.hostname(), node.currentOsVersion(), zone.environment(), zone.region()))
                          .forEach(node -> versions.compute(new OsVersion(node.version(), zone.cloud()), (ignored, nodes) -> {
                              if (nodes == null) {
                                  nodes = new ArrayList<>();
                              }
                              nodes.add(node);
                              return nodes;
                          }));
            }
        }

        return new OsVersionStatus(versions);
    }

    private static List<ZoneId> zonesToUpgrade(Controller controller) {
        return controller.zoneRegistry().osUpgradePolicies().stream()
                         .flatMap(upgradePolicy -> upgradePolicy.asList().stream())
                         .flatMap(Collection::stream)
                         .collect(Collectors.toUnmodifiableList());
    }

    /** A node in this system and its current OS version */
    public static class Node {

        private final HostName hostname;
        private final Version version;
        private final Environment environment;
        private final RegionName region;

        public Node(HostName hostname, Version version, Environment environment, RegionName region) {
            this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
            this.version = Objects.requireNonNull(version, "version must be non-null");
            this.environment = Objects.requireNonNull(environment, "environment must be non-null");
            this.region = Objects.requireNonNull(region, "region must be non-null");
        }

        public HostName hostname() {
            return hostname;
        }

        public Version version() {
            return version;
        }

        public Environment environment() {
            return environment;
        }

        public RegionName region() {
            return region;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return Objects.equals(hostname, node.hostname) &&
                   Objects.equals(version, node.version) &&
                   environment == node.environment &&
                   Objects.equals(region, node.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, version, environment, region);
        }
    }

}
