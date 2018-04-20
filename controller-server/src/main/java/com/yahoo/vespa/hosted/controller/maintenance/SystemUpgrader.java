// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Maintenance job which schedules upgrades of the system.
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
            if (!completeUpgrade(zones, target.get())) {
                break;
            }
        }
    }

    /** Returns true if upgrade of given zones is complete */
    private boolean completeUpgrade(List<ZoneId> zones, Version version) {
        boolean completed = true;
        for (ZoneId zone : zones) {
            startUpgrade(zone, version);
            completed = completed && !isUpgrading(zone);
        }
        return completed;
    }

    /** Returns true if any config servers in given zone are upgrading */
    private boolean isUpgrading(ZoneId zone) {
        return configServerUris(zone).stream().anyMatch(uri -> controller().configServer().version(uri).upgrading());
    }

    /** Schedule upgrade of config servers in given zone, if necessary  */
    private void startUpgrade(ZoneId zone, Version version) {
        configServerUris(zone).stream()
                              .filter(uri -> !controller().configServer().version(uri).wanted().equals(version))
                              .peek(uri -> log.info(String.format("Upgrading config server %s in %s", uri.getHost(),
                                                                  zone)))
                              .forEach(uri -> controller().configServer().upgrade(uri, version));
    }

    /** Returns target version for the system */
    private Optional<Version> targetVersion() {
        return controller().versionStatus().controllerVersion()
                           .filter(vespaVersion -> !vespaVersion.isSystemVersion())
                           .map(VespaVersion::versionNumber);
    }

    private List<URI> configServerUris(ZoneId zone) {
        return controller().zoneRegistry().getConfigServerUris(zone);
    }

}
