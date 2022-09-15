// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.NodeSlice;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersionTarget;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Maintenance job which upgrades system applications.
 *
 * @author mpolden
 */
public class SystemUpgrader extends InfrastructureUpgrader<VespaVersionTarget> {

    private static final Logger log = Logger.getLogger(SystemUpgrader.class.getName());

    private static final Set<Node.State> upgradableNodeStates = Set.of(Node.State.active, Node.State.reserved);

    public SystemUpgrader(Controller controller, Duration interval) {
        super(controller, interval, controller.zoneRegistry().upgradePolicy(), SystemApplication.notController(), null);
    }

    @Override
    protected void upgrade(VespaVersionTarget target, SystemApplication application, ZoneApi zone) {
        log.info(Text.format("Deploying %s on %s in %s", application.id(), target, zone.getId()));
        controller().applications().deploy(application, zone.getId(), target.version(), target.downgrade());
    }

    @Override
    protected boolean convergedOn(VespaVersionTarget target, SystemApplication application, ZoneApi zone, NodeSlice nodeSlice) {
        Optional<Version> currentVersion = versionOf(nodeSlice, zone, application, Node::currentVersion);
        // Skip application convergence check if there are no nodes belonging to the application in the zone
        if (currentVersion.isEmpty()) return true;

        return currentVersion.get().equals(target.version()) &&
               application.configConvergedIn(zone.getId(), controller(), Optional.of(target.version()));
    }

    @Override
    protected boolean expectUpgradeOf(Node node, SystemApplication application, ZoneApi zone) {
        return eligibleForUpgrade(node);
    }

    @Override
    protected Optional<VespaVersionTarget> target() {
        VersionStatus status = controller().readVersionStatus();
        Optional<VespaVersion> target = status.controllerVersion()
                                              .filter(version -> {
                                                  Version systemVersion = status.systemVersion()
                                                                                .map(VespaVersion::versionNumber)
                                                                                .orElse(Version.emptyVersion);
                                                  return version.versionNumber().isAfter(systemVersion);
                                              })
                                              .filter(version -> version.confidence() != VespaVersion.Confidence.broken);
        boolean downgrade = target.isPresent() && target.get().confidence() == VespaVersion.Confidence.aborted;
        if (downgrade) {
            target = status.systemVersion();
        }
        return target.map(VespaVersion::versionNumber)
                     .map(version -> new VespaVersionTarget(version, downgrade));
    }

    @Override
    protected boolean changeTargetTo(VespaVersionTarget target, SystemApplication application, ZoneApi zone) {
        if (application.hasApplicationPackage()) {
            // For applications with package we do not have a zone-wide version target. This means that we must check
            // the wanted version of each node.
            boolean zoneHasSharedRouting = controller().zoneRegistry().routingMethod(zone.getId()).isShared();
            return versionOf(NodeSlice.ALL, zone, application, Node::wantedVersion)
                    .map(wantedVersion -> !wantedVersion.equals(target.version()))
                    .orElse(zoneHasSharedRouting); // Always upgrade if zone uses shared routing, but has no nodes allocated yet
        }
        return controller().serviceRegistry().configServer().nodeRepository()
                           .targetVersionsOf(zone.getId())
                           .vespaVersion(application.nodeType())
                           .map(wantedVersion -> !wantedVersion.equals(target.version()))
                           .orElse(true); // Always set target if there are no nodes
    }

    /** Returns whether node in application should be upgraded by this */
    public static boolean eligibleForUpgrade(Node node) {
        return upgradableNodeStates.contains(node.state());
    }

}
