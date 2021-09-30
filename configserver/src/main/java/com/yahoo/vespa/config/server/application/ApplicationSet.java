// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.component.Version;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable set of {@link Application}s with the same {@link ApplicationId}. With methods for getting defaults.
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

    /**
     * Returns the specified version, or the latest if not specified, or if the given version is not
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

    /** Returns the application for the given version, if available */
    public Optional<Application> get(Version version) {
        return Optional.ofNullable(applications.get(version));
    }

    public ApplicationId getId() { return applicationId; }

    public static ApplicationSet fromList(List<Application> applications) {
        return new ApplicationSet(applications);
    }

    public static ApplicationSet from(Application application) {
        return fromList(List.of(application));
    }

    public Collection<String> getAllHosts() {
        return applications.values().stream()
                .flatMap(app -> app.getModel().getHosts().stream()
                        .map(HostInfo::getHostname))
                .collect(Collectors.toList());
    }

    public void updateHostMetrics() {
        for (Application application : applications.values()) {
            application.updateHostMetrics(application.getModel().getHosts().size());
        }
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
                .collect(Collectors.toList());
    }

}
