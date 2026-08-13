// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.provision.ApplicationId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Immutable set of {@link Application}s with the same {@link ApplicationId},
 * applications have different vespa versions. There will always be at least one application version.
 *
 * @author vegard
 */
public final class ApplicationVersions {

    private final Version latestVersion;
    private final ApplicationId applicationId;
    private final long generation;
    private final HashMap<Version, Application> applications = new HashMap<>();
    private final Provisioned provisioned;

    private ApplicationVersions(List<Application> applications, Provisioned provisioned) {
        if (applications.isEmpty())
            throw new IllegalArgumentException("application list cannot be empty");
        if (applications.stream().map(Application::getId).distinct().count() > 1)
            throw new IllegalArgumentException("All application ids must be equal");
        if (applications.stream().map(Application::getApplicationGeneration).distinct().count() > 1)
            throw new IllegalArgumentException("All config generations must be equal");

        Application firstApp = applications.get(0);
        applicationId = firstApp.getId();
        generation = firstApp.getApplicationGeneration();
        applications.forEach(application -> this.applications.put(application.getVespaVersion(), application));
        latestVersion = this.applications.keySet().stream().max(Version::compareTo).get();
        this.provisioned = provisioned;
    }

    public static ApplicationVersions fromList(List<Application> applications, Provisioned provisioned) {
        return new ApplicationVersions(applications, provisioned);
    }

    /**
     * Returns an application for the specified version, or the latest if not specified, or if the given version is not
     * available and the latest is a permissible substitution.
     *
     * @throws VersionDoesNotExistException if the specified version is not available and the latest version is not
     *                                      a permissible substitution
     */
    public Application getForVersionOrLatest(Optional<Version> optionalVersion, Instant now) {
        Version version = optionalVersion.orElse(latestVersion);
        return resolveForVersion(version, now)
                 .orElseThrow(() -> new VersionDoesNotExistException(applicationId + " has no model for Vespa version " + version));
    }

    /** Returns all the clusters provisioned by all model versions of this (newer versions prioritized). */
    public Provisioned provisioned() { return provisioned; }

    private Optional<Application> resolveForVersion(Version vespaVersion, Instant now) {
        Application application = applications.get(vespaVersion);
        if (application != null)
            return Optional.of(application);

        // Does the latest version specify that we can use it regardless?
        Application latest = applications.get(latestVersion);
        if (latest.getModel().allowModelVersionMismatch(now))
            return Optional.of(latest);

        return Optional.empty();
    }

    /** Returns the application for the given version, or empty if not found */
    public Optional<Application> get(Version version) {
        return Optional.ofNullable(applications.get(version));
    }

    public ApplicationId getId() { return applicationId; }

    public Collection<String> allHosts() {
        return applications.values().stream()
                .flatMap(app -> app.getModel().getHosts().stream()
                        .map(HostInfo::getHostname))
                .distinct()
                .toList();
    }

    public void updateHostMetrics() {
        applications.values().forEach(app -> app.updateHostMetrics(app.getModel().getHosts().size()));
    }

    public long applicationGeneration() {
        return generation;
    }

    public List<Application> applications() {
        return new ArrayList<>(applications.values());
    }

    public List<Version> versions() {
        return applications.values().stream()
                .map(Application::getVespaVersion)
                .sorted()
                .toList();
    }

}
