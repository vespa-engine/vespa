// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.Artifact;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.ArtifactRegistry;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * Periodically expire unused artifacts, e.g. container images and RPMs. Artifacts with a version that is
 * present in config-models-*.xml are never expired (in cd/publiccd we also consider the model versions in main/public).
 *
 * @author mpolden
 */
public class ArtifactExpirer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(ArtifactExpirer.class.getName());

    private static final Duration MIN_AGE = Duration.ofDays(14);

    private final Path configModelPath;

    public ArtifactExpirer(Controller controller, Duration interval) {
        this(controller, interval, Paths.get(Defaults.getDefaults().underVespaHome("conf/configserver-app/")));
    }

    public ArtifactExpirer(Controller controller, Duration interval, Path configModelPath) {
        super(controller, interval);
        this.configModelPath = configModelPath;
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
                    .filter(artifact -> isExpired(artifact, now, versionStatus, modelVersionsInUse()))
                    .toList();
            if (!artifactsToExpire.isEmpty()) {
                log.log(INFO, "Expiring " + artifactsToExpire.size() + " artifacts in " + cloudName + ": " + artifactsToExpire);
                artifactRegistry.deleteAll(artifactsToExpire);
            }
            return 0;
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to expire artifacts in " + cloudName + ". Will retry in " + interval(), e);
            return 1;
        }
    }

    /** Returns whether given artifact is expired */
    private boolean isExpired(Artifact artifact, Instant now, VersionStatus versionStatus, Set<Version> versionsInUse) {
        List<VespaVersion> versions = versionStatus.versions();
        versionsInUse.addAll(versions.stream().map(VespaVersion::versionNumber).collect(Collectors.toSet()));

        if (versionsInUse.contains(artifact.version())) return false;
        if (versionStatus.isActive(artifact.version())) return false;
        if (artifact.createdAt().isAfter(now.minus(MIN_AGE))) return false;

        Version maxVersion = versions.stream().map(VespaVersion::versionNumber).max(Comparator.naturalOrder()).get();
        if (artifact.version().isAfter(maxVersion)) return false; // A future version

        return true;
    }

    /** Model versions in use in this system, and, if this is a CD system, in the main/public system */
    private Set<Version> modelVersionsInUse() {
        var system = controller().system();
        var versions = versionsForSystem(system);

        if (system == SystemName.PublicCd)
            versions.addAll(versionsForSystem(SystemName.Public));
        else if (system == SystemName.cd)
            versions.addAll(versionsForSystem(SystemName.main));

        log.log(FINE, "model versions in use : " + versions);
        return versions;
    }

    private Set<Version> versionsForSystem(SystemName systemName) {
        var versions = readConfigModelVersionsForSystem(systemName.name());
        log.log(FINE, "versions for system " + systemName.name() + ": " + versions);
        return versions;
    }

    private Set<Version> readConfigModelVersionsForSystem(String systemName) {
        List<String> lines = uncheck(() -> Files.readAllLines(configModelPath.resolve("config-models-" + systemName + ".xml")));
        var stringToMatch = "id='VespaModelFactory.";
        return lines.stream()
                .filter(line -> line.contains(stringToMatch))
                .map(line -> {
                    var start = line.indexOf(stringToMatch) + stringToMatch.length();
                    int end = line.indexOf("'", start);
                    return line.substring(start, end);
                })
                .map(Version::fromString)
                .collect(Collectors.toSet());
    }

}
