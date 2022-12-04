// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.ArtifactRegistry;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodically expire unused artifacts, e.g. container images and RPMs.
 *
 * @author mpolden
 */
public class ArtifactExpirer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(ArtifactExpirer.class.getName());

    private static final Duration MIN_AGE = Duration.ofDays(14);

    public ArtifactExpirer(Controller controller, Duration interval) {
        super(controller, interval, null, expiringSystems());
    }

    @Override
    protected double maintain() {
        VersionStatus versionStatus = controller().readVersionStatus();
        return controller().clouds().stream()
                .flatMapToDouble(cloud ->
                    controller().serviceRegistry().artifactRegistry(cloud).stream()
                            .mapToDouble(artifactRegistry -> maintain(versionStatus, cloud, artifactRegistry)))
                .average()
                .orElse(1);
    }

    private double maintain(VersionStatus versionStatus, CloudName cloudName, ArtifactRegistry artifactRegistry) {
        try {
            Instant now = controller().clock().instant();
            List<Artifact> artifactsToExpire = artifactRegistry.list().stream()
                    .filter(artifact -> isExpired(artifact, now, versionStatus))
                    .collect(Collectors.toList());
            if (!artifactsToExpire.isEmpty()) {
                log.log(Level.INFO, "Expiring " + artifactsToExpire.size() + " artifacts in " + cloudName + ": " + artifactsToExpire);
                artifactRegistry.deleteAll(artifactsToExpire);
            }
            return 1;
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to expire artifacts in " + cloudName + ". Will retry in " + interval(), e);
            return 0;
        }
    }

    /** Returns whether given artifact is expired */
    private boolean isExpired(Artifact artifact, Instant now, VersionStatus versionStatus) {
        List<VespaVersion> versions = versionStatus.versions();
        if (versions.isEmpty()) return false;

        if (versionStatus.isActive(artifact.version())) return false;
        if (artifact.createdAt().isAfter(now.minus(MIN_AGE))) return false;

        Version maxVersion = versions.stream().map(VespaVersion::versionNumber).max(Comparator.naturalOrder()).get();
        if (artifact.version().isAfter(maxVersion)) return false; // A future version

        return true;
    }

    /** Returns systems where artifacts can be expired */
    private static Set<SystemName> expiringSystems() {
        // Run only in public and main. Public systems have distinct container registries, while main and CD have
        // shared registries.
        return EnumSet.of(SystemName.Public, SystemName.PublicCd, SystemName.main);
    }

}
