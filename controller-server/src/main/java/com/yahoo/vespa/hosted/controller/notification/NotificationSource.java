// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Denotes the source of the notification.
 *
 * @author freva
 */
public class NotificationSource {
    private final TenantName tenant;
    private final Optional<ApplicationName> application;
    private final Optional<InstanceName> instance;
    private final Optional<ZoneId> zoneId;
    private final Optional<ClusterSpec.Id> clusterId;
    private final Optional<JobType> jobType;
    private final OptionalLong runNumber;

    public NotificationSource(TenantName tenant, Optional<ApplicationName> application, Optional<InstanceName> instance,
                              Optional<ZoneId> zoneId, Optional<ClusterSpec.Id> clusterId, Optional<JobType> jobType, OptionalLong runNumber) {
        this.tenant = Objects.requireNonNull(tenant, "tenant cannot be null");
        this.application = Objects.requireNonNull(application, "application cannot be null");
        this.instance = Objects.requireNonNull(instance, "instance cannot be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId cannot be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId cannot be null");
        this.jobType = Objects.requireNonNull(jobType, "jobType cannot be null");
        this.runNumber = Objects.requireNonNull(runNumber, "runNumber cannot be null");

        if (instance.isPresent() && application.isEmpty())
            throw new IllegalArgumentException("Application name must be present with instance name");
        if (zoneId.isPresent() && instance.isEmpty())
            throw new IllegalArgumentException("Instance name must be present with zone ID");
        if (clusterId.isPresent() && zoneId.isEmpty())
            throw new IllegalArgumentException("Zone ID must be present with cluster ID");
        if (clusterId.isPresent() && jobType.isPresent())
            throw new IllegalArgumentException("Cannot set both cluster ID and job type");
        if (jobType.isPresent() && instance.isEmpty())
            throw new IllegalArgumentException("Instance name must be present with job type");
        if (jobType.isPresent() != runNumber.isPresent())
            throw new IllegalArgumentException(Text.format("Run number (%s) must be 1-to-1 with job type (%s)",
                    runNumber.isPresent() ? "present" : "missing", jobType.map(i -> "present").orElse("missing")));
    }


    public TenantName tenant() { return tenant; }
    public Optional<ApplicationName> application() { return application; }
    public Optional<InstanceName> instance() { return instance; }
    public Optional<ZoneId> zoneId() { return zoneId; }
    public Optional<ClusterSpec.Id> clusterId() { return clusterId; }
    public Optional<JobType> jobType() { return jobType; }
    public OptionalLong runNumber() { return runNumber; }

    /**
     * Returns true iff this source contains the given source. A source contains the other source if
     * all the set fields in this source are equal to the given source, while the fields not set
     * in this source are ignored.
     */
    public boolean contains(NotificationSource other) {
        return tenant.equals(other.tenant) &&
                (application.isEmpty() || application.equals(other.application)) &&
                (instance.isEmpty() || instance.equals(other.instance)) &&
                (zoneId.isEmpty() || zoneId.equals(other.zoneId)) &&
                (clusterId.isEmpty() || clusterId.equals(other.clusterId)) &&
                (jobType.isEmpty() || jobType.equals(other.jobType)); // Do not consider run number (it's unique!)
    }

    /**
     * Returns whether this source from a production deployment or deployment related to prod deployment (e.g. to
     * staging zone), or if this is at tenant or application level
     */
    public boolean isProduction() {
        return ! zoneId.map(ZoneId::environment)
                .or(() -> jobType.map(JobType::environment))
                .map(Environment::isManuallyDeployed)
                .orElse(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationSource that = (NotificationSource) o;
        return tenant.equals(that.tenant) && application.equals(that.application) && instance.equals(that.instance) &&
                zoneId.equals(that.zoneId) && clusterId.equals(that.clusterId) && jobType.equals(that.jobType); // Do not consider run number (it's unique!)
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application, instance, zoneId, clusterId, jobType, runNumber);
    }

    @Override
    public String toString() {
        return "NotificationSource{" +
                "tenant=" + tenant +
                application.map(application -> ", application=" + application.value()).orElse("") +
                instance.map(instance -> ", instance=" + instance.value()).orElse("") +
                zoneId.map(zoneId -> ", zone=" + zoneId.value()).orElse("") +
                clusterId.map(clusterId -> ", clusterId=" + clusterId.value()).orElse("") +
                jobType.map(jobType -> ", job=" + jobType.jobName() + "#" + runNumber.getAsLong()).orElse("") +
                '}';
    }

    private static NotificationSource from(TenantName tenant, ApplicationName application, InstanceName instance, ZoneId zoneId,
                                           ClusterSpec.Id clusterId, JobType jobType, Long runNumber) {
        return new NotificationSource(tenant, Optional.ofNullable(application), Optional.ofNullable(instance), Optional.ofNullable(zoneId),
                Optional.ofNullable(clusterId), Optional.ofNullable(jobType), runNumber == null ? OptionalLong.empty() : OptionalLong.of(runNumber));
    }

    public static NotificationSource from(TenantName tenantName) {
        return from(tenantName, null, null, null, null, null, null);
    }

    public static NotificationSource from(TenantAndApplicationId id) {
        return from(id.tenant(), id.application(), null, null, null, null, null);
    }

    public static NotificationSource from(ApplicationId app) {
        return from(app.tenant(), app.application(), app.instance(), null, null, null, null);
    }

    public static NotificationSource from(DeploymentId deploymentId) {
        ApplicationId app = deploymentId.applicationId();
        return from(app.tenant(), app.application(), app.instance(), deploymentId.zoneId(), null, null, null);
    }

    public static NotificationSource from(DeploymentId deploymentId, ClusterSpec.Id clusterId) {
        ApplicationId app = deploymentId.applicationId();
        return from(app.tenant(), app.application(), app.instance(), deploymentId.zoneId(), clusterId, null, null);
    }

    public static NotificationSource from(RunId runId) {
        ApplicationId app = runId.application();
        return from(app.tenant(), app.application(), app.instance(), null, null, runId.job().type(), runId.number());
    }
}
