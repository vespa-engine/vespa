// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;
import com.yahoo.vespa.hosted.controller.deployment.StepInfo;
import com.yahoo.vespa.hosted.controller.deployment.Versions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.error;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.outOfCapacity;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.success;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.testFailure;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startTests;
import static java.util.Comparator.comparing;

/**
 * Serialises and deserialises {@link Run} objects for persistent storage.
 *
 * @author jonmv
 */
class RunSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    // TODO: Remove "steps" when there are no traces of it in the controllers
    private static final String stepsField = "steps";
    private static final String stepDetailsField = "stepDetails";
    private static final String startTimeField = "startTime";
    private static final String applicationField = "id";
    private static final String jobTypeField = "type";
    private static final String numberField = "number";
    private static final String startField = "start";
    private static final String endField = "end";
    private static final String statusField = "status";
    private static final String versionsField = "versions";
    private static final String platformVersionField = "platform";
    private static final String repositoryField = "repository";
    private static final String branchField = "branch";
    private static final String commitField = "commit";
    private static final String authorEmailField = "authorEmail";
    private static final String compileVersionField = "compileVersion";
    private static final String buildTimeField = "buildTime";
    private static final String sourceUrlField = "sourceUrl";
    private static final String buildField = "build";
    private static final String sourceField = "source";
    private static final String lastTestRecordField = "lastTestRecord";
    private static final String lastVespaLogTimestampField = "lastVespaLogTimestamp";
    private static final String testerCertificateField = "testerCertificate";

    Run runFromSlime(Slime slime) {
        return runFromSlime(slime.get());
    }

    NavigableMap<RunId, Run> runsFromSlime(Slime slime) {
        NavigableMap<RunId, Run> runs = new TreeMap<>(comparing(RunId::number));
        Inspector runArray = slime.get();
        runArray.traverse((ArrayTraverser) (__, runObject) -> {
            Run run = runFromSlime(runObject);
            runs.put(run.id(), run);
        });
        return runs;
    }

    private Run runFromSlime(Inspector runObject) {
        var steps = new EnumMap<Step, StepInfo>(Step.class);
        Inspector detailsField = runObject.field(stepDetailsField);
        runObject.field(stepsField).traverse((ObjectTraverser) (step, status) -> {
            Step typedStep = stepOf(step);

            // For historical reasons are the step details stored in a separate JSON structure from the step statuses.
            Inspector stepDetailsField = detailsField.field(step);
            Inspector startTimeValue = stepDetailsField.field(startTimeField);
            Optional<Instant> startTime = startTimeValue.valid() ?
                    Optional.of(instantOf(startTimeValue.asLong())) :
                    Optional.empty();

            steps.put(typedStep, new StepInfo(typedStep, stepStatusOf(status.asString()), startTime));
        });
        return new Run(new RunId(ApplicationId.fromSerializedForm(runObject.field(applicationField).asString()),
                                 JobType.fromJobName(runObject.field(jobTypeField).asString()),
                                 runObject.field(numberField).asLong()),
                       steps,
                       versionsFromSlime(runObject.field(versionsField)),
                       Instant.ofEpochMilli(runObject.field(startField).asLong()),
                       Optional.of(runObject.field(endField))
                               .filter(Inspector::valid)
                               .map(end -> Instant.ofEpochMilli(end.asLong())),
                       runStatusOf(runObject.field(statusField).asString()),
                       runObject.field(lastTestRecordField).asLong(),
                       Instant.EPOCH.plus(runObject.field(lastVespaLogTimestampField).asLong(), ChronoUnit.MICROS),
                       Optional.of(runObject.field(testerCertificateField))
                               .filter(Inspector::valid)
                               .map(certificate -> X509CertificateUtils.fromPem(certificate.asString())));
    }

    private Versions versionsFromSlime(Inspector versionsObject) {
        Version targetPlatformVersion = Version.fromString(versionsObject.field(platformVersionField).asString());
        ApplicationVersion targetApplicationVersion = applicationVersionFrom(versionsObject);

        Optional<Version> sourcePlatformVersion = versionsObject.field(sourceField).valid()
                ? Optional.of(Version.fromString(versionsObject.field(sourceField).field(platformVersionField).asString()))
                : Optional.empty();
        Optional<ApplicationVersion> sourceApplicationVersion = versionsObject.field(sourceField).valid()
                ? Optional.of(applicationVersionFrom(versionsObject.field(sourceField)))
                : Optional.empty();

        return new Versions(targetPlatformVersion, targetApplicationVersion, sourcePlatformVersion, sourceApplicationVersion);
    }

    private ApplicationVersion applicationVersionFrom(Inspector versionObject) {
        if ( ! versionObject.field(buildField).valid())
            return ApplicationVersion.unknown;

        SourceRevision revision = new SourceRevision(versionObject.field(repositoryField).asString(),
                                                     versionObject.field(branchField).asString(),
                                                     versionObject.field(commitField).asString());
        long buildNumber = versionObject.field(buildField).asLong();
        Optional<String> authorEmail = Serializers.optionalString(versionObject.field(authorEmailField));
        Optional<Version> compileVersion = Serializers.optionalString(versionObject.field(compileVersionField)).map(Version::fromString);
        Optional<Instant> buildTime = Serializers.optionalInstant(versionObject.field(buildTimeField));
        Optional<String> sourceUrl = Serializers.optionalString(versionObject.field(sourceUrlField));
        Optional<String> commit = Serializers.optionalString(versionObject.field(commitField));

        return new ApplicationVersion(Optional.of(revision), OptionalLong.of(buildNumber), authorEmail,
                                      compileVersion, buildTime, sourceUrl, commit);
    }

    Slime toSlime(Iterable<Run> runs) {
        Slime slime = new Slime();
        Cursor runArray = slime.setArray();
        runs.forEach(run -> toSlime(run, runArray.addObject()));
        return slime;
    }

    Slime toSlime(Run run) {
        Slime slime = new Slime();
        toSlime(run, slime.setObject());
        return slime;
    }

    private void toSlime(Run run, Cursor runObject) {
        runObject.setString(applicationField, run.id().application().serializedForm());
        runObject.setString(jobTypeField, run.id().type().jobName());
        runObject.setLong(numberField, run.id().number());
        runObject.setLong(startField, run.start().toEpochMilli());
        run.end().ifPresent(end -> runObject.setLong(endField, end.toEpochMilli()));
        runObject.setString(statusField, valueOf(run.status()));
        runObject.setLong(lastTestRecordField, run.lastTestLogEntry());
        runObject.setLong(lastVespaLogTimestampField, Instant.EPOCH.until(run.lastVespaLogTimestamp(), ChronoUnit.MICROS));
        run.testerCertificate().ifPresent(certificate -> runObject.setString(testerCertificateField, X509CertificateUtils.toPem(certificate)));

        Cursor stepsObject = runObject.setObject(stepsField);
        run.steps().forEach((step, statusInfo) -> stepsObject.setString(valueOf(step), valueOf(statusInfo.status())));

        // For historical reasons are the step details stored in a different field from the step statuses.
        Cursor stepDetailsObject = runObject.setObject(stepDetailsField);
        run.steps().forEach((step, statusInfo) ->
                statusInfo.startTime().ifPresent(startTime ->
                        stepDetailsObject.setObject(valueOf(step)).setLong(startTimeField, valueOf(startTime))));

        Cursor versionsObject = runObject.setObject(versionsField);
        toSlime(run.versions().targetPlatform(), run.versions().targetApplication(), versionsObject);
        run.versions().sourcePlatform().ifPresent(sourcePlatformVersion -> {
            toSlime(sourcePlatformVersion,
                    run.versions().sourceApplication()
                       .orElseThrow(() -> new IllegalArgumentException("Source versions must be both present or absent.")),
                    versionsObject.setObject(sourceField));
        });
    }

    private void toSlime(Version platformVersion, ApplicationVersion applicationVersion, Cursor versionsObject) {
        versionsObject.setString(platformVersionField, platformVersion.toString());
        if ( ! applicationVersion.isUnknown()) {
            versionsObject.setString(repositoryField, applicationVersion.source().get().repository());
            versionsObject.setString(branchField, applicationVersion.source().get().branch());
            versionsObject.setString(commitField, applicationVersion.source().get().commit());
            versionsObject.setLong(buildField, applicationVersion.buildNumber().getAsLong());
        }
        applicationVersion.authorEmail().ifPresent(email -> versionsObject.setString(authorEmailField, email));
        applicationVersion.compileVersion().ifPresent(version -> versionsObject.setString(compileVersionField, version.toString()));
        applicationVersion.buildTime().ifPresent(time -> versionsObject.setLong(buildTimeField, time.toEpochMilli()));
        applicationVersion.sourceUrl().ifPresent(url -> versionsObject.setString(sourceUrlField, url));
        applicationVersion.commit().ifPresent(commit -> versionsObject.setString(commitField, commit));
    }

    static String valueOf(Step step) {
        switch (step) {
            case deployInitialReal  : return "deployInitialReal";
            case installInitialReal : return "installInitialReal";
            case deployReal         : return "deployReal";
            case installReal        : return "installReal";
            case deactivateReal     : return "deactivateReal";
            case deployTester       : return "deployTester";
            case installTester      : return "installTester";
            case deactivateTester   : return "deactivateTester";
            case copyVespaLogs      : return "copyVespaLogs";
            case startTests         : return "startTests";
            case endTests           : return "endTests";
            case report             : return "report";

            default: throw new AssertionError("No value defined for '" + step + "'!");
        }
    }

    static Step stepOf(String step) {
        switch (step) {
            case "deployInitialReal"  : return deployInitialReal;
            case "installInitialReal" : return installInitialReal;
            case "deployReal"         : return deployReal;
            case "installReal"        : return installReal;
            case "deactivateReal"     : return deactivateReal;
            case "deployTester"       : return deployTester;
            case "installTester"      : return installTester;
            case "deactivateTester"   : return deactivateTester;
            case "copyVespaLogs"      : return copyVespaLogs;
            case "startTests"         : return startTests;
            case "endTests"           : return endTests;
            case "report"             : return report;

            default: throw new IllegalArgumentException("No step defined by '" + step + "'!");
        }
    }

    static String valueOf(Status status) {
        switch (status) {
            case unfinished : return "unfinished";
            case failed     : return "failed";
            case succeeded  : return "succeeded";

            default: throw new AssertionError("No value defined for '" + status + "'!");
        }
    }

    static Status stepStatusOf(String status) {
        switch (status) {
            case "unfinished" : return unfinished;
            case "failed"     : return failed;
            case "succeeded"  : return succeeded;

            default: throw new IllegalArgumentException("No status defined by '" + status + "'!");
        }
    }

    static Long valueOf(Instant instant) {
        return instant.toEpochMilli();
    }

    static Instant instantOf(Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
    }

    static String valueOf(RunStatus status) {
        switch (status) {
            case running            : return "running";
            case outOfCapacity      : return "outOfCapacity";
            case deploymentFailed   : return "deploymentFailed";
            case installationFailed : return "installationFailed";
            case testFailure        : return "testFailure";
            case error              : return "error";
            case success            : return "success";
            case aborted            : return "aborted";

            default: throw new AssertionError("No value defined for '" + status + "'!");
        }
    }

    static RunStatus runStatusOf(String status) {
        switch (status) {
            case "running"            : return running;
            case "outOfCapacity"      : return outOfCapacity;
            case "deploymentFailed"   : return deploymentFailed;
            case "installationFailed" : return installationFailed;
            case "testFailure"        : return testFailure;
            case "error"              : return error;
            case "success"            : return success;
            case "aborted"            : return aborted;

            default: throw new IllegalArgumentException("No run status defined by '" + status + "'!");
        }
    }

}
