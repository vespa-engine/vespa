// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableList;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.maintenance.OsUpgrader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Information about OS versions in this system.
 *
 * @author mpolden
 */
public class OsVersionStatus {

    public static final OsVersionStatus empty = new OsVersionStatus(Collections.emptyList());

    private final List<OsVersion> versions;

    public OsVersionStatus(List<OsVersion> versions) {
        this.versions = ImmutableList.copyOf(versions);
    }

    /** All known OS versions */
    public List<OsVersion> versions() {
        return versions;
    }

    /**
     * Compute the current OS version status in this status. This is expensive as all config servers in the system
     * must be queried.
     */
    public static OsVersionStatus compute(Controller controller) {
        List<OsVersion.Node> versions = new ArrayList<>();
        for (SystemApplication application : SystemApplication.all()) {
            if (application.nodeTypesWithUpgradableOs().isEmpty()) {
                continue; // Avoid querying applications that do not have nodes with upgradable OS
            }
            for (ZoneId zone : controller.zoneRegistry().zones().controllerUpgraded().ids()) {
                controller.configServer().nodeRepository().list(zone, application.id()).stream()
                          .filter(node -> OsUpgrader.eligibleForUpgrade(node, application))
                          .map(node -> new OsVersion.Node(node.hostname(), node.currentOsVersion(), zone.environment(), zone.region()))
                          .forEach(versions::add);
            }
        }
        return new OsVersionStatus(versions.stream()
                                           .collect(Collectors.groupingBy(OsVersion.Node::version))
                                           .entrySet()
                                           .stream()
                                           .map(kv -> new OsVersion(kv.getKey(), kv.getValue()))
                                           .sorted(Comparator.comparing(OsVersion::version))
                                           .collect(Collectors.toList()));
    }

}
