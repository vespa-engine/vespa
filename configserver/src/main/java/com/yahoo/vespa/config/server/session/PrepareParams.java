// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.SessionHandler;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Parameters for prepare.
 *
 * @author lulf
 * @since 5.1.24
 */
public final class PrepareParams {

    static final String APPLICATION_NAME_PARAM_NAME = "applicationName";
    static final String INSTANCE_PARAM_NAME = "instance";
    static final String IGNORE_VALIDATION_PARAM_NAME = "ignoreValidationErrors";
    static final String DRY_RUN_PARAM_NAME = "dryRun";
    static final String VESPA_VERSION_PARAM_NAME = "vespaVersion";
    static final String ROTATIONS_PARAM_NAME = "rotations";
    static final String DOCKER_VESPA_IMAGE_VERSION_PARAM_NAME = "dockerVespaImageVersion";

    private boolean ignoreValidationErrors = false;
    private boolean dryRun = false;
    private ApplicationId applicationId = ApplicationId.defaultId();
    private TimeoutBudget timeoutBudget;
    private Optional<Version> vespaVersion = Optional.empty();
    private Set<Rotation> rotations;
    private Optional<Version> dockerVespaImageVersion = Optional.empty();

    PrepareParams() {
        this(new ConfigserverConfig(new ConfigserverConfig.Builder()));
    }

    public PrepareParams(ConfigserverConfig configserverConfig) {
        timeoutBudget = new TimeoutBudget(Clock.systemUTC(), getBarrierTimeout(configserverConfig));
    }

    public PrepareParams applicationId(ApplicationId applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    public PrepareParams ignoreValidationErrors(boolean ignoreValidationErrors) {
        this.ignoreValidationErrors = ignoreValidationErrors;
        return this;
    }

    public PrepareParams dryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public PrepareParams timeoutBudget(TimeoutBudget timeoutBudget) {
        this.timeoutBudget = timeoutBudget;
        return this;
    }

    public PrepareParams vespaVersion(String vespaVersion) {
        Optional<Version> version = Optional.empty();
        if (vespaVersion != null && !vespaVersion.isEmpty()) {
            version = Optional.of(Version.fromString(vespaVersion));
        }
        this.vespaVersion = version;
        return this;
    }

    public PrepareParams rotations(String rotationsString) {
        this.rotations = new LinkedHashSet<>();
        if (rotationsString != null && !rotationsString.isEmpty()) {
            String[] rotations = rotationsString.split(",");
            for (String s : rotations) {
                this.rotations.add(new Rotation(s));
            }
        }
        return this;
    }

    public PrepareParams dockerVespaImageVersion(String dockerVespaImageVersion) {
        Optional<Version> version = Optional.empty();
        if (dockerVespaImageVersion != null && !dockerVespaImageVersion.isEmpty()) {
            version = Optional.of(Version.fromString(dockerVespaImageVersion));
        }
        this.dockerVespaImageVersion = version;
        return this;
    }

    public static PrepareParams fromHttpRequest(HttpRequest request, TenantName tenant, ConfigserverConfig configserverConfig) {
        return new PrepareParams(configserverConfig).ignoreValidationErrors(request.getBooleanProperty(IGNORE_VALIDATION_PARAM_NAME))
                                  .dryRun(request.getBooleanProperty(DRY_RUN_PARAM_NAME))
                                  .timeoutBudget(SessionHandler.getTimeoutBudget(request, getBarrierTimeout(configserverConfig)))
                                  .applicationId(createApplicationId(request, tenant))
                                  .vespaVersion(request.getProperty(VESPA_VERSION_PARAM_NAME))
                                  .rotations(request.getProperty(ROTATIONS_PARAM_NAME))
                                  .dockerVespaImageVersion(request.getProperty(DOCKER_VESPA_IMAGE_VERSION_PARAM_NAME));
    }

    private static Duration getBarrierTimeout(ConfigserverConfig configserverConfig) {
        return Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
    }

    private static ApplicationId createApplicationId(HttpRequest request, TenantName tenant) {
        return new ApplicationId.Builder()
               .tenant(tenant)
               .applicationName(getPropertyWithDefault(request, APPLICATION_NAME_PARAM_NAME, "default"))
               .instanceName(getPropertyWithDefault(request, INSTANCE_PARAM_NAME, "default"))
               .build();
    }

    private static String getPropertyWithDefault(HttpRequest request, String propertyName, String defaultProperty) {
        return getProperty(request, propertyName).orElse(defaultProperty);
    }

    private static Optional<String> getProperty(HttpRequest request, String propertyName) {
        return Optional.ofNullable(request.getProperty(propertyName));
    }
    
    public String getApplicationName() {
        return applicationId.application().value();
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public Optional<Version> vespaVersion() { return vespaVersion; }

    public Set<Rotation> rotations() { return rotations; }

    public boolean ignoreValidationErrors() {
        return ignoreValidationErrors;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public TimeoutBudget getTimeoutBudget() {
        return timeoutBudget;
    }

    public Optional<Version> getVespaVersion() {
        return vespaVersion;
    }

    public Set<Rotation> getRotations() {
        return rotations;
    }

    public Optional<Version> getDockerVespaImageVersion() {
        return dockerVespaImageVersion;
    }
}
