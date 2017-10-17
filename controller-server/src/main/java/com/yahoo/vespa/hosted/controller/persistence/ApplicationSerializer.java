// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    
    // Deployment fields
    private final String zoneField = "zone";
    private final String environmentField = "environment";
    private final String regionField = "region";
    private final String deployTimeField = "deployTime";
    private final String applicationPackageRevisionField = "applicationPackageRevision";
    private final String applicationPackageHashField = "applicationPackageHash";
    private final String sourceRevisionField = "sourceRevision";
    private final String repositoryField = "repositoryField";
    private final String branchField = "branchField";
    private final String commitField = "commitField";
    
    // DeploymentJobs fields
    private final String projectIdField = "projectId";
    private final String jobStatusField = "jobStatus";
    private final String jiraIssueIdField = "jiraIssueId";
    
    // JobStatus field
    private final String jobTypeField = "jobType";
    private final String errorField = "jobError";
    private final String lastTriggeredField = "lastTriggered";
    private final String lastCompletedField = "lastCompleted";
    private final String firstFailingField = "firstFailing";
    private final String lastSuccessField = "lastSuccess";
    
    // JobRun fields
    private final String versionField = "version";
    private final String revisionField = "revision";
    private final String atField = "at";
    private final String upgradeField = "upgrade";

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
        toSlime(application.deploying(), root);
        root.setBool(outstandingChangeField, application.hasOutstandingChange());
        return slime;
    }

    private void deploymentsToSlime(Collection<Deployment> deployments, Cursor array) {
        for (Deployment deployment : deployments)
            deploymentToSlime(deployment, array.addObject());
    }
    
    private void deploymentToSlime(Deployment deployment, Cursor object) {
        zoneToSlime(deployment.zone(), object.setObject(zoneField));
        object.setString(versionField, deployment.version().toString());
        object.setLong(deployTimeField, deployment.at().toEpochMilli());
        toSlime(deployment.revision(), object.setObject(applicationPackageRevisionField));
        clusterInfoToSlime(deployment.clusterInfo(), object);
        clusterUtilsToSlime(deployment.clusterUtils(), object);
        metricsToSlime(deployment.metrics(), object);
    }

    private void metricsToSlime(DeploymentMetrics metrics, Cursor object) {
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

    private void zoneToSlime(Zone zone, Cursor object) {
        object.setString(environmentField, zone.environment().value());
        object.setString(regionField, zone.region().value());
    }
    
    private void toSlime(ApplicationRevision applicationRevision, Cursor object) {
        object.setString(applicationPackageHashField, applicationRevision.id());
        if (applicationRevision.source().isPresent())
            toSlime(applicationRevision.source().get(), object.setObject(sourceRevisionField));
    }
    
    private void toSlime(SourceRevision sourceRevision, Cursor object) {
        object.setString(repositoryField, sourceRevision.repository());
        object.setString(branchField, sourceRevision.branch());
        object.setString(commitField, sourceRevision.commit());
    }
    
    private void toSlime(DeploymentJobs deploymentJobs, Cursor cursor) {
        deploymentJobs.projectId()
                .filter(id -> id > 0) // TODO: Discards invalid data. Remove filter after October 2017
                .ifPresent(projectId -> cursor.setLong(projectIdField, projectId));
        jobStatusToSlime(deploymentJobs.jobStatus().values(), cursor.setArray(jobStatusField));
        deploymentJobs.jiraIssueId().ifPresent(jiraIssueId -> cursor.setString(jiraIssueIdField, jiraIssueId));
    }

    private void jobStatusToSlime(Collection<JobStatus> jobStatuses, Cursor jobStatusArray) {
        for (JobStatus jobStatus : jobStatuses)
            toSlime(jobStatus, jobStatusArray.addObject());
    }
    
    private void toSlime(JobStatus jobStatus, Cursor object) {
        object.setString(jobTypeField, jobStatus.type().id());
        if (jobStatus.jobError().isPresent())
            object.setString(errorField, jobStatus.jobError().get().name());

        jobRunToSlime(jobStatus.lastTriggered(), object, lastTriggeredField);
        jobRunToSlime(jobStatus.lastCompleted(), object, lastCompletedField);
        jobRunToSlime(jobStatus.firstFailing(), object, firstFailingField);
        jobRunToSlime(jobStatus.lastSuccess(), object, lastSuccessField);
    }
    
    private void jobRunToSlime(Optional<JobStatus.JobRun> jobRun, Cursor parent, String jobRunObjectName) {
        if ( ! jobRun.isPresent()) return;
        Cursor object = parent.setObject(jobRunObjectName);
        object.setString(versionField, jobRun.get().version().toString());
        if ( jobRun.get().revision().isPresent())
            toSlime(jobRun.get().revision().get(), object.setObject(revisionField));
        object.setBool(upgradeField, jobRun.get().upgrade());
        object.setLong(atField, jobRun.get().at().toEpochMilli());
    }
    
    private void toSlime(Optional<Change> deploying, Cursor parentObject) {
        if ( ! deploying.isPresent()) return;

        Cursor object = parentObject.setObject(deployingField);
        if (deploying.get() instanceof Change.VersionChange)
            object.setString(versionField, ((Change.VersionChange)deploying.get()).version().toString());
        else if (((Change.ApplicationChange)deploying.get()).revision().isPresent())
            toSlime(((Change.ApplicationChange)deploying.get()).revision().get(), object);
    }

    // ------------------ Deserialization

    public Application fromSlime(Slime slime) {
        Inspector root = slime.get();
        
        ApplicationId id = ApplicationId.fromSerializedForm(root.field(idField).asString());
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(root.field(deploymentSpecField).asString(), false);
        ValidationOverrides validationOverrides = ValidationOverrides.fromXml(root.field(validationOverridesField).asString());
        List<Deployment> deployments = deploymentsFromSlime(root.field(deploymentsField));
        DeploymentJobs deploymentJobs = deploymentJobsFromSlime(root.field(deploymentJobsField));
        Optional<Change> deploying = changeFromSlime(root.field(deployingField));
        boolean outstandingChange = root.field(outstandingChangeField).asBool();

        return new Application(id, deploymentSpec, validationOverrides, deployments, 
                               deploymentJobs, deploying, outstandingChange);
    }

    private List<Deployment> deploymentsFromSlime(Inspector array) {
        List<Deployment> deployments = new ArrayList<>();
        array.traverse((ArrayTraverser) (int i, Inspector item) -> deployments.add(deploymentFromSlime(item)));
        return deployments;
    }

    private Deployment deploymentFromSlime(Inspector deploymentObject) {
        return new Deployment(zoneFromSlime(deploymentObject.field(zoneField)),
                              applicationRevisionFromSlime(deploymentObject.field(applicationPackageRevisionField)).get(),
                              Version.fromString(deploymentObject.field(versionField).asString()),
                              Instant.ofEpochMilli(deploymentObject.field(deployTimeField).asLong()),
                clusterUtilsMapFromSlime(deploymentObject.field(clusterUtilsField)),
                clusterInfoMapFromSlime(deploymentObject.field(clusterInfoField)),
                deploymentMetricsFromSlime(deploymentObject.field(deploymentMetricsField)));
    }

    private DeploymentMetrics deploymentMetricsFromSlime(Inspector object) {

        double queriesPerSecond = object.field(deploymentMetricsQPSField).asDouble();
        double writesPerSecond = object.field(deploymentMetricsWPSField).asDouble();
        double documentCount = object.field(deploymentMetricsDocsField).asDouble();
        double queryLatencyMillis = object.field(deploymentMetricsQueryLatencyField).asDouble();
        double writeLatencyMills = object.field(deploymentMetricsWriteLatencyField).asDouble();

        return new DeploymentMetrics(queriesPerSecond, writesPerSecond,
                documentCount, queryLatencyMillis, writeLatencyMills);
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

    private Zone zoneFromSlime(Inspector object) {
        return new Zone(Environment.from(object.field(environmentField).asString()),
                        RegionName.from(object.field(regionField).asString()));
    }

    private Optional<ApplicationRevision> applicationRevisionFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        String applicationPackageHash = object.field(applicationPackageHashField).asString();
        Optional<SourceRevision> sourceRevision = sourceRevisionFromSlime(object.field(sourceRevisionField));
        return sourceRevision.isPresent() ? Optional.of(ApplicationRevision.from(applicationPackageHash, sourceRevision.get()))
                                          : Optional.of(ApplicationRevision.from(applicationPackageHash));
    }
    
    private Optional<SourceRevision> sourceRevisionFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(new SourceRevision(object.field(repositoryField).asString(),
                                              object.field(branchField).asString(),
                                              object.field(commitField).asString()));
    }

    private DeploymentJobs deploymentJobsFromSlime(Inspector object) {
        Optional<Long> projectId = optionalLong(object.field(projectIdField))
                .filter(id -> id > 0); // TODO: Discards invalid data. Remove filter after October 2017
        List<JobStatus> jobStatusList = jobStatusListFromSlime(object.field(jobStatusField));
        Optional<String> jiraIssueKey = optionalString(object.field(jiraIssueIdField));

        return new DeploymentJobs(projectId, jobStatusList, jiraIssueKey);
    }

    private Optional<Change> changeFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        Inspector versionFieldValue = object.field(versionField);
        if (versionFieldValue.valid())
            return Optional.of(new Change.VersionChange(Version.fromString(versionFieldValue.asString())));
        else if (object.field(applicationPackageHashField).valid())
            return Optional.of(Change.ApplicationChange.of(applicationRevisionFromSlime(object).get()));
        else
            return Optional.of(Change.ApplicationChange.unknown());
    }
    
    private List<JobStatus> jobStatusListFromSlime(Inspector array) {
        List<JobStatus> jobStatusList = new ArrayList<>();
        array.traverse((ArrayTraverser) (int i, Inspector item) -> jobStatusList.add(jobStatusFromSlime(item)));
        return jobStatusList;
    }
    
    private JobStatus jobStatusFromSlime(Inspector object) {
        DeploymentJobs.JobType jobType = DeploymentJobs.JobType.fromId(object.field(jobTypeField).asString());

        Optional<JobError> jobError = Optional.empty();
        if (object.field(errorField).valid())
            jobError = Optional.of(JobError.valueOf(object.field(errorField).asString()));

        return new JobStatus(jobType, jobError,
                             jobRunFromSlime(object.field(lastTriggeredField)),
                             jobRunFromSlime(object.field(lastCompletedField)),
                             jobRunFromSlime(object.field(firstFailingField)),
                             jobRunFromSlime(object.field(lastSuccessField)));
    }

    private Optional<JobStatus.JobRun> jobRunFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(new JobStatus.JobRun(new Version(object.field(versionField).asString()),
                                                applicationRevisionFromSlime(object.field(revisionField)),
                                                object.field(upgradeField).asBool(),
                                                Instant.ofEpochMilli(object.field(atField).asLong())));
    }

    private Optional<Long> optionalLong(Inspector field) {
        return field.valid() ? Optional.of(field.asLong()) : Optional.empty();
    }

    private Optional<String> optionalString(Inspector field) {
        return SlimeUtils.optionalString(field);
    }

}
