// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Maintenance job that schedules upgrades of OS / kernel on nodes in the system.
 *
 * @author mpolden
 */
public class OsUpgrader extends InfrastructureUpgrader {

    private static final Set<Node.State> upgradableNodeStates = ImmutableSet.of(
            Node.State.ready,
            Node.State.active,
            Node.State.reserved
    );

    public OsUpgrader(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl, controller.zoneRegistry().osUpgradePolicy());
    }

    @Override
    protected void maintain() {
        if (controller().system() != SystemName.cd) return; // TODO: Enable in all systems
        super.maintain();
    }

    @Override
    protected void upgrade(Version target, SystemApplication application, ZoneId zone) {
        application.nodeTypesWithUpgradableOs().forEach(nodeType -> controller().configServer().nodeRepository()
                                                                                .upgradeOs(zone, nodeType, target));
    }

    @Override
    protected boolean convergedOn(Version target, SystemApplication application, ZoneId zone) {
        return currentVersion(zone, application, target).equals(target);
    }

    @Override
    protected boolean requireUpgradeOf(Node node, SystemApplication application) {
        return eligibleForUpgrade(node, application);
    }

    private Version currentVersion(ZoneId zone, SystemApplication application, Version defaultVersion) {
        return minVersion(zone, application, Node::currentOsVersion).orElse(defaultVersion);
    }

    @Override
    protected Optional<Version> targetVersion() {
        // TODO: Read a computed version status instead, and only return a target if there are nodes on a lower version
        return controller().curator().readOsTargetVersion();
    }

    /** Returns whether node in application should be upgraded by this */
    public static boolean eligibleForUpgrade(Node node, SystemApplication application) {
        return upgradableNodeStates.contains(node.state()) && application.nodeTypesWithUpgradableOs().contains(node.type());
    }

}
