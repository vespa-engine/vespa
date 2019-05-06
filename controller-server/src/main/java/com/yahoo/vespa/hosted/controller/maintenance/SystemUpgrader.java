// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Duration;
import java.util.EnumSet;
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

    private static final Set<Node.State> upgradableNodeStates = EnumSet.of(Node.State.active, Node.State.reserved);

    public SystemUpgrader(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl, controller.zoneRegistry().upgradePolicy(), null);
    }

    @Override
    protected void upgrade(Version target, SystemApplication application, ZoneId zone) {
        if (minVersion(zone, application, Node::wantedVersion).map(target::isAfter)
                                                              .orElse(true)) {
            log.info(String.format("Deploying %s version %s in %s", application.id(), target, zone));
            controller().applications().deploy(application, zone, target);
        }
    }

    @Override
    protected boolean convergedOn(Version target, SystemApplication application, ZoneId zone) {
        return    minVersion(zone, application, Node::currentVersion).map(target::equals)
                                                                     .orElse(true)
               && application.configConvergedIn(zone, controller());
    }

    @Override
    protected boolean requireUpgradeOf(Node node, SystemApplication application, ZoneId zone) {
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
