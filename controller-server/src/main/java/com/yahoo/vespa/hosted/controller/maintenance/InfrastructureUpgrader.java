// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.NodeSlice;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.versions.VersionTarget;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Base class for maintainers that upgrade zone infrastructure.
 *
 * @author mpolden
 */
public abstract class InfrastructureUpgrader<TARGET extends VersionTarget> extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(InfrastructureUpgrader.class.getName());

    protected final UpgradePolicy upgradePolicy;
    private final List<SystemApplication> managedApplications;

    public InfrastructureUpgrader(Controller controller, Duration interval, UpgradePolicy upgradePolicy,
                                  List<SystemApplication> managedApplications, String name) {
        super(controller, interval, name, EnumSet.allOf(SystemName.class));
        this.upgradePolicy = upgradePolicy;
        this.managedApplications = List.copyOf(Objects.requireNonNull(managedApplications));
    }

    @Override
    protected double maintain() {
        return target().map(target -> upgradeAll(target, managedApplications))
                       .orElse(1.0);
    }

    /** Deploy a list of system applications until they converge on the given version */
    private double upgradeAll(TARGET target, List<SystemApplication> applications) {
        int attempts = 0;
        int failures = 0;
        // Invert zone order if we're downgrading
        UpgradePolicy policy = target.downgrade() ? upgradePolicy.inverted() : upgradePolicy;
        for (UpgradePolicy.Step step : policy.steps()) {
            boolean converged = true;
            for (ZoneApi zone : step.zones()) {
                try {
                    attempts++;
                    converged &= upgradeAll(target, applications, zone, step.nodeSlice());
                } catch (UnreachableNodeRepositoryException e) {
                    failures++;
                    converged = false;
                    log.warning(Text.format("%s: Failed to communicate with node repository in %s, continuing with next parallel zone: %s",
                                            this, zone, Exceptions.toMessageString(e)));
                } catch (Exception e) {
                    failures++;
                    converged = false;
                    log.warning(Text.format("%s: Failed to upgrade zone: %s, continuing with next parallel zone: %s",
                                            this, zone, Exceptions.toMessageString(e)));
                }
            }
            if (!converged) {
                break;
            }
        }
        return asSuccessFactor(attempts, failures);
    }

    /** Returns whether all applications have converged to the target version in zone */
    private boolean upgradeAll(TARGET target, List<SystemApplication> applications, ZoneApi zone, NodeSlice nodeSlice) {
        Map<SystemApplication, Set<SystemApplication>> dependenciesByApplication = new HashMap<>();
        if (target.downgrade()) { // Invert dependencies when we're downgrading
            for (var application : applications) {
                dependenciesByApplication.computeIfAbsent(application, k -> new HashSet<>());
                for (var dependency : application.dependencies()) {
                    dependenciesByApplication.computeIfAbsent(dependency, k -> new HashSet<>())
                                             .add(application);
                }
            }
        } else {
            applications.forEach(app -> dependenciesByApplication.put(app, Set.copyOf(app.dependencies())));
        }
        boolean converged = true;
        for (var kv : dependenciesByApplication.entrySet()) {
            SystemApplication application = kv.getKey();
            Set<SystemApplication> dependencies = kv.getValue();
            boolean allConverged = dependencies.stream().allMatch(app -> convergedOn(target, app, zone, nodeSlice));
            if (allConverged) {
                if (changeTargetTo(target, application, zone)) {
                    upgrade(target, application, zone);
                }
                converged &= convergedOn(target, application, zone, nodeSlice);
            }
        }
        return converged;
    }

    /** Returns whether target version for application in zone should be changed */
    protected abstract boolean changeTargetTo(TARGET target, SystemApplication application, ZoneApi zone);

    /** Upgrade component to target version. Implementation should be idempotent */
    protected abstract void upgrade(TARGET target, SystemApplication application, ZoneApi zone);

    /** Returns whether application has converged to target version in zone */
    protected abstract boolean convergedOn(TARGET target, SystemApplication application, ZoneApi zone, NodeSlice nodeSlice);

    /** Returns the version target for the component upgraded by this, if any */
    protected abstract Optional<TARGET> target();

    /** Returns whether the upgrader should expect given node to upgrade */
    protected abstract boolean expectUpgradeOf(Node node, SystemApplication application, ZoneApi zone);

    /** Find the highest version used by nodes satisfying nodeSlice in zone. If no such slice exists, the lowest known version is returned */
    protected final Optional<Version> versionOf(NodeSlice nodeSlice, ZoneApi zone, SystemApplication application, Function<Node, Version> versionField) {
        try {
            Map<Version, Long> nodeCountByVersion = controller().serviceRegistry().configServer()
                                                                .nodeRepository()
                                                                .list(zone.getVirtualId(), NodeFilter.all().applications(application.id()))
                                                                .stream()
                                                                .filter(node -> expectUpgradeOf(node, application, zone))
                                                                .collect(Collectors.groupingBy(versionField,
                                                                                               Collectors.counting()));
            long totalNodes = nodeCountByVersion.values().stream().reduce(Long::sum).orElse(0L);
            Set<Version> versionsOfMatchingSlices = new HashSet<>();
            for (var kv : nodeCountByVersion.entrySet()) {
                long nodesOnVersion = kv.getValue();
                if (nodeSlice.satisfiedBy(nodesOnVersion, totalNodes)) {
                    versionsOfMatchingSlices.add(kv.getKey());
                }
            }
            if (!versionsOfMatchingSlices.isEmpty()) {
                // Choose the highest version in case we have several matching slices
                return versionsOfMatchingSlices.stream().max(Comparator.naturalOrder());
            }
            // No matching slices found, fall back to the lowest known version
            return nodeCountByVersion.keySet().stream().min(Comparator.naturalOrder());
        } catch (Exception e) {
            throw new UnreachableNodeRepositoryException(Text.format("Failed to get version for %s in %s: %s",
                                                                     application.id(), zone,
                                                                     Exceptions.toMessageString(e)));
        }
    }

    private static class UnreachableNodeRepositoryException extends RuntimeException {
        private UnreachableNodeRepositoryException(String reason) {
            super(reason);
        }
    }

}
