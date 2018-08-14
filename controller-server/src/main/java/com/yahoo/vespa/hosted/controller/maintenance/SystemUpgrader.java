// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
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

    private static final Set<Node.State> upgradableNodeStates = ImmutableSet.of(Node.State.active, Node.State.reserved);

    public SystemUpgrader(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl, controller.zoneRegistry().upgradePolicy());
    }

    @Override
    protected void upgrade(Version target, SystemApplication application, ZoneId zone) {
        if (wantedVersion(zone, application, target).equals(target)) {
            return;
        }
        log.info(String.format("Deploying %s version %s in %s", application.id(), target, zone));
        controller().applications().deploy(application, zone, target);
    }

    @Override
    protected boolean convergedOn(Version target, SystemApplication application, ZoneId zone) {
        return currentVersion(zone, application, target).equals(target) && application.configConvergedIn(zone, controller());
    }

    @Override
    protected boolean requireUpgradeOf(Node node, SystemApplication application) {
        return eligibleForUpgrade(node);
    }

    @Override
    protected Optional<Version> targetVersion() {
        return controller().versionStatus().controllerVersion()
                           .filter(vespaVersion -> !vespaVersion.isSystemVersion())
                           .map(VespaVersion::versionNumber);
    }

    private Version wantedVersion(ZoneId zone, SystemApplication application, Version defaultVersion) {
        return minVersion(zone, application, Node::wantedVersion).orElse(defaultVersion);
    }

    private Version currentVersion(ZoneId zone, SystemApplication application, Version defaultVersion) {
        return minVersion(zone, application, Node::currentVersion).orElse(defaultVersion);
    }

    /** Returns whether node in application should be upgraded by this */
    public static boolean eligibleForUpgrade(Node node) {
        return upgradableNodeStates.contains(node.state());
    }

}
