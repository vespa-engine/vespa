// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
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
public class SystemUpgrader extends InfrastructureUpgrader {

    private static final Logger log = Logger.getLogger(SystemUpgrader.class.getName());

    private static final Set<Node.State> upgradableNodeStates = Set.of(Node.State.active, Node.State.reserved);

    public SystemUpgrader(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl, controller.zoneRegistry().upgradePolicy(), null);
    }

    @Override
    protected void upgrade(Version target, SystemApplication application, ZoneApi zone) {
        // TODO(mpolden): Simplify this by comparing with version from NodeRepository#targetVersionsOf instead
        if (minVersion(zone, application, Node::wantedVersion).map(target::isAfter)
                                                              .orElse(true)) {
            log.info(String.format("Deploying %s version %s in %s", application.id(), target, zone.getId()));
            controller().applications().deploy(application, zone.getId(), target);
        }
    }

    @Override
    protected boolean convergedOn(Version target, SystemApplication application, ZoneApi zone) {
        Optional<Version> minVersion = minVersion(zone, application, Node::currentVersion);
        // Skip application convergence check if there are no nodes belonging to the application in the zone
        if (minVersion.isEmpty()) return true;

        return     minVersion.get().equals(target)
                && application.configConvergedIn(zone.getId(), controller(), Optional.of(target));
    }

    @Override
    protected boolean requireUpgradeOf(Node node, SystemApplication application, ZoneApi zone) {
        return eligibleForUpgrade(node);
    }

    @Override
    protected Optional<Version> targetVersion() {
        return controller().versionStatus().controllerVersion()
                           .filter(vespaVersion -> !vespaVersion.isSystemVersion())
                           .filter(vespaVersion -> vespaVersion.confidence() != VespaVersion.Confidence.broken)
                           .map(VespaVersion::versionNumber);
    }

    /** Returns whether node in application should be upgraded by this */
    public static boolean eligibleForUpgrade(Node node) {
        return upgradableNodeStates.contains(node.state());
    }

}
