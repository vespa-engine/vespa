// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.zone.NodeSlice;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Trigger OS upgrade of zones in the system, according to the current OS version target.
 *
 * Target OS version is set per cloud, and an instance of this exists per cloud in the system.
 *
 * {@link OsUpgradeScheduler} may update the target automatically in supported clouds.
 *
 * @author mpolden
 */
public class OsUpgrader extends InfrastructureUpgrader<OsVersionTarget> {

    private static final Logger log = Logger.getLogger(OsUpgrader.class.getName());

    private static final Set<Node.State> upgradableNodeStates = Set.of(
            Node.State.ready,
            Node.State.active,
            Node.State.reserved
    );

    private final CloudName cloud;

    public OsUpgrader(Controller controller, Duration interval, CloudName cloud) {
        super(controller, interval, controller.zoneRegistry().osUpgradePolicy(cloud), SystemApplication.all(), name(cloud));
        this.cloud = cloud;
    }

    @Override
    protected void upgrade(OsVersionTarget target, SystemApplication application, ZoneApi zone) {
        log.info(Text.format("Upgrading OS of %s to version %s in %s in cloud %s", application.id(),
                               target.osVersion().version().toFullString(),
                               zone.getVirtualId(), zone.getCloudName()));
        controller().serviceRegistry().configServer().nodeRepository().upgradeOs(zone.getVirtualId(), application.nodeType(),
                                                                                 target.osVersion().version());
    }

    @Override
    protected boolean convergedOn(OsVersionTarget target, SystemApplication application, ZoneApi zone, NodeSlice nodeSlice) {
        Version currentVersion = versionOf(nodeSlice, zone, application, Node::currentOsVersion).orElse(target.osVersion().version());
        return !currentVersion.isBefore(target.osVersion().version());
    }

    @Override
    protected boolean expectUpgradeOf(Node node, SystemApplication application, ZoneApi zone) {
        return cloud.equals(zone.getCloudName()) && // Cloud is managed by this upgrader
               application.shouldUpgradeOs() &&     // Application should upgrade in this cloud
               canUpgrade(node, false);
    }

    @Override
    protected Optional<OsVersionTarget> target() {
        // Return target if we have nodes in this cloud on a lower version
        return controller().osVersionTarget(cloud)
                           .filter(target -> controller().osVersionStatus().nodesIn(cloud).stream()
                                                         .anyMatch(node -> node.currentVersion().isBefore(target.osVersion().version())));
    }

    @Override
    protected boolean changeTargetTo(OsVersionTarget target, SystemApplication application, ZoneApi zone) {
        if (!application.shouldUpgradeOs()) return false;
        return controller().serviceRegistry().configServer().nodeRepository()
                           .targetVersionsOf(zone.getVirtualId())
                           .osVersion(application.nodeType())
                           .map(currentTarget -> target.osVersion().version().isAfter(currentTarget))
                           .orElse(true);
    }

    /** Returns whether node currently allows upgrades */
    public static boolean canUpgrade(Node node, boolean includeDeferring) {
        return (includeDeferring || !node.deferOsUpgrade()) && upgradableNodeStates.contains(node.state());
    }

    private static String name(CloudName cloud) {
        return capitalize(cloud.value()) + OsUpgrader.class.getSimpleName(); // Prefix maintainer name with cloud name
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char firstLetter = Character.toUpperCase(s.charAt(0));
        if (s.length() > 1) {
            return firstLetter + s.substring(1).toLowerCase();
        }
        return String.valueOf(firstLetter);
    }

}
