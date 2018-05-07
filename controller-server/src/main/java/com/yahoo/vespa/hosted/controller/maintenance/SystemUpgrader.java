// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintenance job which upgrades system applications.
 *
 * @author mpolden
 */
public class SystemUpgrader extends Maintainer {

    private static final Logger log = Logger.getLogger(SystemUpgrader.class.getName());

    public SystemUpgrader(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        Optional<Version> target = targetVersion();
        if (!target.isPresent()) {
            return;
        }
        for (List<ZoneId> zones : controller().zoneRegistry().upgradePolicy().asList()) {
            // The order here is important. Config servers should always upgrade first
            if (!deploy(zones, SystemApplication.configServer, target.get())) {
                break;
            }
            if (!deploy(zones, SystemApplication.zone, target.get())) {
                break;
            }
        }
    }

    /** Deploy application on given version. Returns true when all allocated nodes are on requested version */
    private boolean deploy(List<ZoneId> zones, SystemApplication application, Version version) {
        boolean completed = true;
        for (ZoneId zone : zones) {
            if (!wantedVersion(zone, application.id()).equals(version)) {
                log.info(String.format("Deploying %s version %s in %s", application.id(), version, zone));
                controller().applications().deploy(application, zone, version);
            }
            completed = completed && currentVersion(zone, application.id()).equals(version);
        }
        return completed;
    }

    private Version wantedVersion(ZoneId zone, ApplicationId application) {
        return minVersion(zone, application, Node::wantedVersion);
    }

    private Version currentVersion(ZoneId zone, ApplicationId application) {
        return minVersion(zone, application, Node::currentVersion);
    }

    private Version minVersion(ZoneId zone, ApplicationId application, Function<Node, Version> versionField) {
        try {
            return controller().configServer()
                               .nodeRepository()
                               .list(zone, application)
                               .stream()
                               .map(versionField)
                               .min(Comparator.naturalOrder())
                               .orElse(Version.emptyVersion);
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Failed to get version for %s in %s: %s", application, zone,
                                                 Exceptions.toMessageString(e)));
            return Version.emptyVersion;
        }
    }

    /** Returns target version for the system */
    private Optional<Version> targetVersion() {
        return controller().versionStatus().controllerVersion()
                           .filter(vespaVersion -> !vespaVersion.isSystemVersion())
                           .map(VespaVersion::versionNumber);
    }

}
