// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
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
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml(root.field(deploymentSpecField).asString());
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
                new HashMap<>(), new HashMap<>()); //TODO
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
