// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ApplicationId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Immutable set of {@link Application}s with the same {@link ApplicationId}, applications have difference vespa versions.
 *
 * @author vegard
 */
public final class ApplicationSet {

    private final Version latestVersion;
    private final ApplicationId applicationId;
    private final long generation;
    private final HashMap<Version, Application> applications = new HashMap<>();

    private ApplicationSet(List<Application> applications) {
        if (applications.isEmpty()) throw new IllegalArgumentException("application list cannot be empty");

        Application firstApp = applications.get(0);
        applicationId = firstApp.getId();
        generation = firstApp.getApplicationGeneration();
        for (Application application : applications) {
            this.applications.put(application.getVespaVersion(), application);
            ApplicationId applicationId = application.getId();
            if ( ! applicationId.equals(this.applicationId)) {
                throw new IllegalArgumentException("Trying to create set with different application ids (" +
                                                   application + " and " + this.applicationId + ")");
            }
            if ( ! application.getApplicationGeneration().equals(generation)) {
                throw new IllegalArgumentException("Trying to create set with different generations ("  +
                                                   generation + " and " + this.generation + ")");
            }
        }
        latestVersion = this.applications.keySet().stream().max(Version::compareTo).get();
    }

    public static ApplicationSet fromList(List<Application> applications) {
        return new ApplicationSet(applications);
    }

    // For testing
    public static ApplicationSet from(Application application) {
        return fromList(List.of(application));
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

    private Optional<Application> resolveForVersion(Version vespaVersion, Instant now) {
        Application application = applications.get(vespaVersion);
        if (application != null)
            return Optional.of(application);

        // Does the latest version specify we can use it regardless?
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

    public Collection<String> getAllHosts() {
        return applications.values().stream()
                .flatMap(app -> app.getModel().getHosts().stream()
                        .map(HostInfo::getHostname))
                .toList();
    }

    public void updateHostMetrics() {
        applications.values().forEach(app -> app.updateHostMetrics(app.getModel().getHosts().size()));
    }

    public long getApplicationGeneration() {
        return generation;
    }

    List<Application> getAllApplications() {
        return new ArrayList<>(applications.values());
    }

    public List<Version> getAllVersions(ApplicationId applicationId) {
        return applications.values().stream()
                .filter(application -> application.getId().equals(applicationId))
                .map(Application::getVespaVersion)
                .sorted()
                .toList();
    }

}
