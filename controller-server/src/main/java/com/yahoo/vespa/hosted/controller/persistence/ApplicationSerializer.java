// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentActivity;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Serializes applications to/from slime.
 * This class is multithread safe.
 *
 * @author bratseth
 */
public class ApplicationSerializer {

    // Application fields
    private final String idField = "id";
    private final String deploymentSpecField = "deploymentSpecField";
    private final String validationOverridesField = "validationOverrides";
    private final String deploymentsField = "deployments";
    private final String deploymentJobsField = "deploymentJobs";
    private final String deployingField = "deployingField";
    private final String outstandingChangeField = "outstandingChangeField";
    private final String ownershipIssueIdField = "ownershipIssueId";
    private final String writeQualityField = "writeQuality";
    private final String queryQualityField = "queryQuality";
    private final String rotationField = "rotation";

    // Deployment fields
    private final String zoneField = "zone";
    private final String environmentField = "environment";
    private final String regionField = "region";
    private final String deployTimeField = "deployTime";
    private final String applicationBuildNumberField = "applicationBuildNumber";
    private final String applicationPackageRevisionField = "applicationPackageRevision";
    private final String sourceRevisionField = "sourceRevision";
    private final String repositoryField = "repositoryField";
    private final String branchField = "branchField";
    private final String commitField = "commitField";
    private final String lastQueriedField = "lastQueried";
    private final String lastWrittenField = "lastWritten";
    private final String lastQueriesPerSecondField = "lastQueriesPerSecond";
    private final String lastWritesPerSecondField = "lastWritesPerSecond";

    // DeploymentJobs fields
    private final String projectIdField = "projectId";
    private final String jobStatusField = "jobStatus";
    private final String issueIdField = "jiraIssueId";

    // JobStatus field
    private final String jobTypeField = "jobType";
    private final String errorField = "jobError";
    private final String lastTriggeredField = "lastTriggered";
    private final String lastCompletedField = "lastCompleted";
    private final String firstFailingField = "firstFailing";
    private final String lastSuccessField = "lastSuccess";

    // JobRun fields
    private final String jobRunIdField = "id";
    private final String versionField = "version";
    private final String revisionField = "revision";
    private final String sourceVersionField = "sourceVersion";
    private final String sourceApplicationField = "sourceRevision";
    private final String reasonField = "reason";
    private final String atField = "at";

    // ClusterInfo fields
    private final String clusterInfoField = "clusterInfo";
    private final String clusterInfoFlavorField = "flavor";
    private final String clusterInfoCostField = "cost";
    private final String clusterInfoCpuField = "flavorCpu";
    private final String clusterInfoMemField = "flavorMem";
    private final String clusterInfoDiskField = "flavorDisk";
    private final String clusterInfoTypeField = "clusterType";
    private final String clusterInfoHostnamesField = "hostnames";

    // ClusterUtils fields
    private final String clusterUtilsField = "clusterUtils";
    private final String clusterUtilsCpuField = "cpu";
    private final String clusterUtilsMemField = "mem";
    private final String clusterUtilsDiskField = "disk";
    private final String clusterUtilsDiskBusyField = "diskbusy";

    // Deployment metrics fields
    private final String deploymentMetricsField = "metrics";
    private final String deploymentMetricsQPSField = "queriesPerSecond";
    private final String deploymentMetricsWPSField = "writesPerSecond";
    private final String deploymentMetricsDocsField = "documentCount";
    private final String deploymentMetricsQueryLatencyField = "queryLatencyMillis";
    private final String deploymentMetricsWriteLatencyField = "writeLatencyMillis";


    // ------------------ Serialization

    public Slime toSlime(Application application) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(idField, application.id().serializedForm());
        root.setString(deploymentSpecField, application.deploymentSpec().xmlForm());
        root.setString(validationOverridesField, application.validationOverrides().xmlForm());
        deploymentsToSlime(application.deployments().values(), root.setArray(deploymentsField));
        toSlime(application.deploymentJobs(), root.setObject(deploymentJobsField));
        toSlime(application.change(), root, deployingField);
        toSlime(application.outstandingChange(), root, outstandingChangeField);
        application.ownershipIssueId().ifPresent(issueId -> root.setString(ownershipIssueIdField, issueId.value()));
        root.setDouble(queryQualityField, application.metrics().queryServiceQuality());
        root.setDouble(writeQualityField, application.metrics().writeServiceQuality());
        application.rotation().ifPresent(rotation -> root.setString(rotationField, rotation.id().asString()));
        return slime;
    }

    private void deploymentsToSlime(Collection<Deployment> deployments, Cursor array) {
        for (Deployment deployment : deployments)
            deploymentToSlime(deployment, array.addObject());
    }

    private void deploymentToSlime(Deployment deployment, Cursor object) {
        zoneIdToSlime(deployment.zone(), object.setObject(zoneField));
        object.setString(versionField, deployment.version().toString());
        object.setLong(deployTimeField, deployment.at().toEpochMilli());
        toSlime(deployment.applicationVersion(), object.setObject(applicationPackageRevisionField));
        clusterInfoToSlime(deployment.clusterInfo(), object);
        clusterUtilsToSlime(deployment.clusterUtils(), object);
        deploymentMetricsToSlime(deployment.metrics(), object);
        deployment.activity().lastQueried().ifPresent(instant -> object.setLong(lastQueriedField, instant.toEpochMilli()));
        deployment.activity().lastWritten().ifPresent(instant -> object.setLong(lastWrittenField, instant.toEpochMilli()));
        deployment.activity().lastQueriesPerSecond().ifPresent(value -> object.setDouble(lastQueriesPerSecondField, value));
        deployment.activity().lastWritesPerSecond().ifPresent(value -> object.setDouble(lastWritesPerSecondField, value));
    }

    private void deploymentMetricsToSlime(DeploymentMetrics metrics, Cursor object) {
        Cursor root = object.setObject(deploymentMetricsField);
        root.setDouble(deploymentMetricsQPSField, metrics.queriesPerSecond());
        root.setDouble(deploymentMetricsWPSField, metrics.writesPerSecond());
        root.setDouble(deploymentMetricsDocsField, metrics.documentCount());
        root.setDouble(deploymentMetricsQueryLatencyField, metrics.queryLatencyMillis());
        root.setDouble(deploymentMetricsWriteLatencyField, metrics.writeLatencyMillis());
    }

    private void clusterInfoToSlime(Map<ClusterSpec.Id, ClusterInfo> clusters, Cursor object) {
        Cursor root = object.setObject(clusterInfoField);
        for (Map.Entry<ClusterSpec.Id, ClusterInfo> entry : clusters.entrySet()) {
            toSlime(entry.getValue(), root.setObject(entry.getKey().value()));
        }
    }

    private void toSlime(ClusterInfo info, Cursor object) {
        object.setString(clusterInfoFlavorField, info.getFlavor());
        object.setLong(clusterInfoCostField, info.getFlavorCost());
        object.setDouble(clusterInfoCpuField, info.getFlavorCPU());
        object.setDouble(clusterInfoMemField, info.getFlavorMem());
        object.setDouble(clusterInfoDiskField, info.getFlavorDisk());
        object.setString(clusterInfoTypeField, info.getClusterType().name());
        Cursor array = object.setArray(clusterInfoHostnamesField);
        for (String host : info.getHostnames()) {
            array.addString(host);
        }
    }

    private void clusterUtilsToSlime(Map<ClusterSpec.Id, ClusterUtilization> clusters, Cursor object) {
        Cursor root = object.setObject(clusterUtilsField);
        for (Map.Entry<ClusterSpec.Id, ClusterUtilization> entry : clusters.entrySet()) {
            toSlime(entry.getValue(), root.setObject(entry.getKey().value()));
        }
    }

    private void toSlime(ClusterUtilization utils, Cursor object) {
        object.setDouble(clusterUtilsCpuField, utils.getCpu());
        object.setDouble(clusterUtilsMemField, utils.getMemory());
        object.setDouble(clusterUtilsDiskField, utils.getDisk());
        object.setDouble(clusterUtilsDiskBusyField, utils.getDiskBusy());
    }

    private void zoneIdToSlime(ZoneId zone, Cursor object) {
        object.setString(environmentField, zone.environment().value());
        object.setString(regionField, zone.region().value());
    }

    private void toSlime(ApplicationVersion applicationVersion, Cursor object) {
        if (applicationVersion.buildNumber().isPresent() && applicationVersion.source().isPresent()) {
            object.setLong(applicationBuildNumberField, applicationVersion.buildNumber().get());
            toSlime(applicationVersion.source().get(), object.setObject(sourceRevisionField));
        }
    }

    private void toSlime(SourceRevision sourceRevision, Cursor object) {
        object.setString(repositoryField, sourceRevision.repository());
        object.setString(branchField, sourceRevision.branch());
        object.setString(commitField, sourceRevision.commit());
    }

    private void toSlime(DeploymentJobs deploymentJobs, Cursor cursor) {
        deploymentJobs.projectId().ifPresent(projectId -> cursor.setLong(projectIdField, projectId));
        jobStatusToSlime(deploymentJobs.jobStatus().values(), cursor.setArray(jobStatusField));
        deploymentJobs.issueId().ifPresent(jiraIssueId -> cursor.setString(issueIdField, jiraIssueId.value()));
    }

    private void jobStatusToSlime(Collection<JobStatus> jobStatuses, Cursor jobStatusArray) {
        for (JobStatus jobStatus : jobStatuses)
            toSlime(jobStatus, jobStatusArray.addObject());
    }

    private void toSlime(JobStatus jobStatus, Cursor object) {
        object.setString(jobTypeField, jobStatus.type().jobName());
        if (jobStatus.jobError().isPresent())
            object.setString(errorField, jobStatus.jobError().get().name());

        jobStatus.lastTriggered().ifPresent(run -> jobRunToSlime(run, object, lastTriggeredField));
        jobStatus.lastCompleted().ifPresent(run -> jobRunToSlime(run, object, lastCompletedField));
        jobStatus.lastSuccess().ifPresent(run -> jobRunToSlime(run, object, lastSuccessField));
        jobStatus.firstFailing().ifPresent(run -> jobRunToSlime(run, object, firstFailingField));
    }

    private void jobRunToSlime(JobStatus.JobRun jobRun, Cursor parent, String jobRunObjectName) {
        Cursor object = parent.setObject(jobRunObjectName);
        object.setLong(jobRunIdField, jobRun.id());
        object.setString(versionField, jobRun.platform().toString());
        toSlime(jobRun.application(), object.setObject(revisionField));
        jobRun.sourcePlatform().ifPresent(version -> object.setString(sourceVersionField, version.toString()));
        jobRun.sourceApplication().ifPresent(version -> toSlime(version, object.setObject(sourceApplicationField)));
        object.setString(reasonField, jobRun.reason());
        object.setLong(atField, jobRun.at().toEpochMilli());
    }

    private void toSlime(Change deploying, Cursor parentObject, String fieldName) {
        if ( ! deploying.isPresent()) return;

        Cursor object = parentObject.setObject(fieldName);
        if (deploying.platform().isPresent())
            object.setString(versionField, deploying.platform().get().toString());
        if (deploying.application().isPresent())
            toSlime(deploying.application().get(), object);
    }

    // ------------------ Deserialization

    public Application fromSlime(Slime slime) {
        Inspector root = slime.get();

        ApplicationId id = ApplicationId.fromSerializedForm(root.field(idField).asString());
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(root.field(deploymentSpecField).asString(), false);
        ValidationOverrides validationOverrides = ValidationOverrides.fromXml(root.field(validationOverridesField).asString());
        List<Deployment> deployments = deploymentsFromSlime(root.field(deploymentsField));
        DeploymentJobs deploymentJobs = deploymentJobsFromSlime(root.field(deploymentJobsField));
        Change deploying = changeFromSlime(root.field(deployingField));
        Change outstandingChange = changeFromSlime(root.field(outstandingChangeField));
        Optional<IssueId> ownershipIssueId = optionalString(root.field(ownershipIssueIdField)).map(IssueId::from);
        ApplicationMetrics metrics = new ApplicationMetrics(root.field(queryQualityField).asDouble(),
                                                            root.field(writeQualityField).asDouble());
        Optional<RotationId> rotation = rotationFromSlime(root.field(rotationField));

        return new Application(id, deploymentSpec, validationOverrides, deployments, deploymentJobs, deploying,
                               outstandingChange, ownershipIssueId, metrics, rotation);
    }

    private List<Deployment> deploymentsFromSlime(Inspector array) {
        List<Deployment> deployments = new ArrayList<>();
        array.traverse((ArrayTraverser) (int i, Inspector item) -> deployments.add(deploymentFromSlime(item)));
        return deployments;
    }

    private Deployment deploymentFromSlime(Inspector deploymentObject) {
        return new Deployment(zoneIdFromSlime(deploymentObject.field(zoneField)),
                              applicationVersionFromSlime(deploymentObject.field(applicationPackageRevisionField)),
                              Version.fromString(deploymentObject.field(versionField).asString()),
                              Instant.ofEpochMilli(deploymentObject.field(deployTimeField).asLong()),
                              clusterUtilsMapFromSlime(deploymentObject.field(clusterUtilsField)),
                              clusterInfoMapFromSlime(deploymentObject.field(clusterInfoField)),
                              deploymentMetricsFromSlime(deploymentObject.field(deploymentMetricsField)),
                              DeploymentActivity.create(optionalInstant(deploymentObject.field(lastQueriedField)),
                                                        optionalInstant(deploymentObject.field(lastWrittenField)),
                                                        optionalDouble(deploymentObject.field(lastQueriesPerSecondField)),
                                                        optionalDouble(deploymentObject.field(lastWritesPerSecondField))));
    }

    private DeploymentMetrics deploymentMetricsFromSlime(Inspector object) {
        return new DeploymentMetrics(object.field(deploymentMetricsQPSField).asDouble(),
                                     object.field(deploymentMetricsWPSField).asDouble(),
                                     object.field(deploymentMetricsDocsField).asDouble(),
                                     object.field(deploymentMetricsQueryLatencyField).asDouble(),
                                     object.field(deploymentMetricsWriteLatencyField).asDouble());
    }

    private Map<ClusterSpec.Id, ClusterInfo> clusterInfoMapFromSlime(Inspector object) {
        Map<ClusterSpec.Id, ClusterInfo> map = new HashMap<>();
        object.traverse((String name, Inspector obect) -> map.put(new ClusterSpec.Id(name), clusterInfoFromSlime(obect)));
        return map;
    }

    private Map<ClusterSpec.Id, ClusterUtilization> clusterUtilsMapFromSlime(Inspector object) {
        Map<ClusterSpec.Id, ClusterUtilization> map = new HashMap<>();
        object.traverse((String name, Inspector value) -> map.put(new ClusterSpec.Id(name), clusterUtililzationFromSlime(value)));
        return map;
    }

    private ClusterUtilization clusterUtililzationFromSlime(Inspector object) {
        double cpu = object.field(clusterUtilsCpuField).asDouble();
        double mem = object.field(clusterUtilsMemField).asDouble();
        double disk = object.field(clusterUtilsDiskField).asDouble();
        double diskBusy = object.field(clusterUtilsDiskBusyField).asDouble();

        return new ClusterUtilization(mem, cpu, disk, diskBusy);
    }

    private ClusterInfo clusterInfoFromSlime(Inspector inspector) {
        String flavor = inspector.field(clusterInfoFlavorField).asString();
        int cost = (int)inspector.field(clusterInfoCostField).asLong();
        String type = inspector.field(clusterInfoTypeField).asString();
        double flavorCpu = inspector.field(clusterInfoCpuField).asDouble();
        double flavorMem = inspector.field(clusterInfoMemField).asDouble();
        double flavorDisk = inspector.field(clusterInfoDiskField).asDouble();

        List<String> hostnames = new ArrayList<>();
        inspector.field(clusterInfoHostnamesField).traverse((ArrayTraverser)(int index, Inspector value) -> hostnames.add(value.asString()));
        return new ClusterInfo(flavor, cost, flavorCpu, flavorMem, flavorDisk, ClusterSpec.Type.from(type), hostnames);
    }

    private ZoneId zoneIdFromSlime(Inspector object) {
        return ZoneId.from(object.field(environmentField).asString(), object.field(regionField).asString());
    }

    private ApplicationVersion applicationVersionFromSlime(Inspector object) {
        if ( ! object.valid()) return ApplicationVersion.unknown;
        OptionalLong applicationBuildNumber = optionalLong(object.field(applicationBuildNumberField));
        Optional<SourceRevision> sourceRevision = sourceRevisionFromSlime(object.field(sourceRevisionField));
        if (!sourceRevision.isPresent() || !applicationBuildNumber.isPresent()) {
            return ApplicationVersion.unknown;
        }
        return ApplicationVersion.from(sourceRevision.get(), applicationBuildNumber.getAsLong());
    }

    private Optional<SourceRevision> sourceRevisionFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(new SourceRevision(object.field(repositoryField).asString(),
                                              object.field(branchField).asString(),
                                              object.field(commitField).asString()));
    }

    private DeploymentJobs deploymentJobsFromSlime(Inspector object) {
        OptionalLong projectId = optionalLong(object.field(projectIdField));
        List<JobStatus> jobStatusList = jobStatusListFromSlime(object.field(jobStatusField));
        Optional<IssueId> issueId = optionalString(object.field(issueIdField)).map(IssueId::from);

        return new DeploymentJobs(projectId, jobStatusList, issueId);
    }

    private Change changeFromSlime(Inspector object) {
        if ( ! object.valid()) return Change.empty();
        Inspector versionFieldValue = object.field(versionField);
        Change change = Change.empty();
        if (versionFieldValue.valid())
            change = Change.of(Version.fromString(versionFieldValue.asString()));
        if (object.field(applicationBuildNumberField).valid())
            change = change.with(applicationVersionFromSlime(object));
        if ( ! change.isPresent()) // A deploy object with no fields -> unknown application change
            change = Change.empty();
        return change;
    }

    private List<JobStatus> jobStatusListFromSlime(Inspector array) {
        List<JobStatus> jobStatusList = new ArrayList<>();
        array.traverse((ArrayTraverser) (int i, Inspector item) -> jobStatusFromSlime(item).ifPresent(jobStatusList::add));
        return jobStatusList;
    }

    private Optional<JobStatus> jobStatusFromSlime(Inspector object) {
        // if the job type has since been removed, ignore it
        Optional<DeploymentJobs.JobType> jobType =
                DeploymentJobs.JobType.fromOptionalJobName(object.field(jobTypeField).asString());
        if (! jobType.isPresent()) return Optional.empty();

        Optional<JobError> jobError = Optional.empty();
        if (object.field(errorField).valid())
            jobError = Optional.of(JobError.valueOf(object.field(errorField).asString()));

        return Optional.of(new JobStatus(jobType.get(), jobError,
                             jobRunFromSlime(object.field(lastTriggeredField)),
                             jobRunFromSlime(object.field(lastCompletedField)),
                             jobRunFromSlime(object.field(firstFailingField)),
                             jobRunFromSlime(object.field(lastSuccessField))));
    }

    private Optional<JobStatus.JobRun> jobRunFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(new JobStatus.JobRun(object.field(jobRunIdField).asLong(),
                                                new Version(object.field(versionField).asString()),
                                                applicationVersionFromSlime(object.field(revisionField)),
                                                optionalString(object.field(sourceVersionField)).map(Version::fromString),
                                                Optional.of(object.field(sourceApplicationField)).filter(Inspector::valid).map(this::applicationVersionFromSlime),
                                                object.field(reasonField).asString(),
                                                Instant.ofEpochMilli(object.field(atField).asLong())));
    }

    private Optional<RotationId> rotationFromSlime(Inspector field) {
        return field.valid() ? optionalString(field).map(RotationId::new) : Optional.empty();
    }

    private OptionalLong optionalLong(Inspector field) {
        return field.valid() ? OptionalLong.of(field.asLong()) : OptionalLong.empty();
    }

    private OptionalDouble optionalDouble(Inspector field) {
        return field.valid() ? OptionalDouble.of(field.asDouble()) : OptionalDouble.empty();
    }

    private Optional<String> optionalString(Inspector field) {
        return SlimeUtils.optionalString(field);
    }

    private Optional<Instant> optionalInstant(Inspector field) {
        OptionalLong value = optionalLong(field);
        return value.isPresent() ? Optional.of(Instant.ofEpochMilli(value.getAsLong())) : Optional.empty();
    }

}
