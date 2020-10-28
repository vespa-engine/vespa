// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Maintenance job which upgrades system applications.
 *
 * @author mpolden
 */
public class SystemUpgrader extends InfrastructureUpgrader<Version> {

    private static final Logger log = Logger.getLogger(SystemUpgrader.class.getName());

    private static final Set<Node.State> upgradableNodeStates = Set.of(Node.State.active, Node.State.reserved);

    public SystemUpgrader(Controller controller, Duration interval) {
        super(controller, interval, controller.zoneRegistry().upgradePolicy(), null);
    }

    @Override
    protected void upgrade(Version target, SystemApplication application, ZoneApi zone) {
        log.info(String.format("Deploying %s version %s in %s", application.id(), target, zone.getId()));
        controller().applications().deploy(application, zone.getId(), target);
    }

    @Override
    protected boolean convergedOn(Version target, SystemApplication application, ZoneApi zone) {
        Optional<Version> minVersion = minVersion(zone, application, Node::currentVersion);
        // Skip application convergence check if there are no nodes belonging to the application in the zone
        if (minVersion.isEmpty()) return true;

        return minVersion.get().equals(target) &&
               application.configConvergedIn(zone.getId(), controller(), Optional.of(target));
    }

    @Override
    protected boolean expectUpgradeOf(Node node, SystemApplication application, ZoneApi zone) {
        return eligibleForUpgrade(node);
    }

    @Override
    protected Optional<Version> targetVersion() {
        return controller().readVersionStatus().controllerVersion()
                           .filter(vespaVersion -> !vespaVersion.isSystemVersion())
                           .filter(vespaVersion -> vespaVersion.confidence() != VespaVersion.Confidence.broken)
                           .map(VespaVersion::versionNumber);
    }

    @Override
    protected boolean changeTargetTo(Version target, SystemApplication application, ZoneApi zone) {
        if (application.hasApplicationPackage()) {
            // For applications with package we do not have a zone-wide version target. This means that we must check
            // the wanted version of each node.
            boolean zoneHasSharedRouting = controller().zoneRegistry().routingMethods(zone.getId()).stream()
                                                       .anyMatch(RoutingMethod::isShared);
            return minVersion(zone, application, Node::wantedVersion)
                    .map(target::isAfter)          // Upgrade if target is after any wanted version
                    .orElse(zoneHasSharedRouting); // Always upgrade if zone uses shared routing, but has no nodes allocated yet

        }
        return controller().serviceRegistry().configServer().nodeRepository()
                           .targetVersionsOf(zone.getId())
                           .vespaVersion(application.nodeType())
                           .map(target::isAfter)                              // Upgrade if target is after current
                           .orElse(true);                                     // Upgrade if target is unset
    }

    /** Returns whether node in application should be upgraded by this */
    public static boolean eligibleForUpgrade(Node node) {
        return upgradableNodeStates.contains(node.state());
    }

}
