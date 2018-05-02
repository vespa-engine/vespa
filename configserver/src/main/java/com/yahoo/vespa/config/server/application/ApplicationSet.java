// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Version;

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
    // TODO: Should not need these as part of application?
    private final ApplicationId applicationId;
    private final long generation;
    private final HashMap<Version, Application> applications = new HashMap<>();

    private ApplicationSet(List<Application> applicationList) {
        applicationId = applicationList.get(0).getId();
        generation = applicationList.get(0).getApplicationGeneration();
        for (Application application : applicationList) {
            applications.put(application.getVespaVersion(), application);
            if (!application.getId().equals(applicationId)) {
                throw new IllegalArgumentException("Trying to create set with different application ids");
            }
        }
        latestVersion = applications.keySet().stream().max((a, b) -> a.compareTo(b)).get();
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

    public static ApplicationSet fromSingle(Application application) {
        return fromList(Arrays.asList(application));
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
}
