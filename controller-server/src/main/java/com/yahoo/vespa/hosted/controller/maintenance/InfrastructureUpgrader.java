// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Base class for maintainers that upgrade zone infrastructure.
 *
 * @author mpolden
 */
public abstract class InfrastructureUpgrader extends Maintainer {

    private static final Logger log = Logger.getLogger(InfrastructureUpgrader.class.getName());

    private final UpgradePolicy upgradePolicy;

    public InfrastructureUpgrader(Controller controller, Duration interval, JobControl jobControl,
                                  UpgradePolicy upgradePolicy, String name) {
        super(controller, interval, jobControl, name, EnumSet.allOf(SystemName.class));
        this.upgradePolicy = upgradePolicy;
    }

    @Override
    protected void maintain() {
        targetVersion().ifPresent(target -> upgradeAll(target, SystemApplication.all()));
    }

    /** Deploy a list of system applications until they converge on the given version */
    private void upgradeAll(Version target, List<SystemApplication> applications) {
        for (List<ZoneApi> zones : upgradePolicy.asList()) {
            boolean converged = true;
            for (ZoneApi zone : zones) {
                try {
                    converged &= upgradeAll(target, applications, zone);
                } catch (UnreachableNodeRepositoryException e) {
                    converged = false;
                    log.warning(String.format("%s: Failed to communicate with node repository in %s, continuing with next parallel zone: %s", this, zone, Exceptions.toMessageString(e)));
                } catch (Exception e) {
                    converged = false;
                    log.warning(String.format("%s: Failed to upgrade zone: %s, continuing with next parallel zone: %s", this, zone, Exceptions.toMessageString(e)));
                }
            }
            if (!converged) {
                break;
            }
        }
    }

    /** Returns whether all applications have converged to the target version in zone */
    private boolean upgradeAll(Version target, List<SystemApplication> applications, ZoneApi zone) {
        boolean converged = true;
        for (SystemApplication application : applications) {
            if (convergedOn(target, application.dependencies(), zone)) {
                boolean currentAppConverged = convergedOn(target, application, zone);
                // In dynamically provisioned zones there may be no tenant hosts at the time of upgrade, so we
                // should always set the target version.
                if (application == SystemApplication.tenantHost || !currentAppConverged) {
                    upgrade(target, application, zone);
                }
                converged &= currentAppConverged;
            }
        }
        return converged;
    }

    private boolean convergedOn(Version target, List<SystemApplication> applications, ZoneApi zone) {
        return applications.stream().allMatch(application -> convergedOn(target, application, zone));
    }

    /** Upgrade component to target version. Implementation should be idempotent */
    protected abstract void upgrade(Version target, SystemApplication application, ZoneApi zone);

    /** Returns whether application has converged to target version in zone */
    protected abstract boolean convergedOn(Version target, SystemApplication application, ZoneApi zone);

    /** Returns the target version for the component upgraded by this, if any */
    protected abstract Optional<Version> targetVersion();

    /** Returns whether the upgrader should require given node to upgrade */
    protected abstract boolean requireUpgradeOf(Node node, SystemApplication application, ZoneApi zone);

    /** Find the minimum value of a version field in a zone */
    protected final Optional<Version> minVersion(ZoneApi zone, SystemApplication application, Function<Node, Version> versionField) {
        try {
            return controller().serviceRegistry().configServer()
                               .nodeRepository()
                               .list(zone.getId(), application.id())
                               .stream()
                               .filter(node -> requireUpgradeOf(node, application, zone))
                               .map(versionField)
                               .min(Comparator.naturalOrder());
        } catch (Exception e) {
            throw new UnreachableNodeRepositoryException(String.format("Failed to get version for %s in %s: %s",
                                                                       application.id(), zone, Exceptions.toMessageString(e)));
        }
    }

    private class UnreachableNodeRepositoryException extends RuntimeException {
        private UnreachableNodeRepositoryException(String reason) {
            super(reason);
        }
    }

}
