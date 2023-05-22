// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.ConvergenceSummary;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;
import com.yahoo.vespa.hosted.controller.deployment.StepInfo;
import com.yahoo.vespa.hosted.controller.deployment.Versions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.cancelled;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.endpointCertificateTimeout;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.error;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.invalidApplication;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.noTests;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.nodeAllocationFailure;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.quotaExceeded;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.reset;
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
import static com.yahoo.vespa.hosted.controller.deployment.Step.endStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startStagingSetup;
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

    private static final String stepsField = "steps";
    private static final String stepDetailsField = "stepDetails";
    private static final String startTimeField = "startTime";
    private static final String applicationField = "id";
    private static final String jobTypeField = "type";
    private static final String numberField = "number";
    private static final String startField = "start";
    private static final String endField = "end";
    private static final String sleepingUntilField = "sleepingUntil";
    private static final String statusField = "status";
    private static final String versionsField = "versions";
    private static final String isRedeploymentField = "isRedeployment";
    private static final String platformVersionField = "platform";
    private static final String deployedDirectlyField = "deployedDirectly";
    private static final String buildField = "build";
    private static final String sourceField = "source";
    private static final String lastTestRecordField = "lastTestRecord";
    private static final String lastVespaLogTimestampField = "lastVespaLogTimestamp";
    private static final String noNodesDownSinceField = "noNodesDownSince";
    private static final String convergenceSummaryField = "convergenceSummaryV2";
    private static final String testerCertificateField = "testerCertificate";
    private static final String isDryRunField = "isDryRun";
    private static final String reasonField = "reason";

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
        return Collections.unmodifiableNavigableMap(runs);
    }

    private Run runFromSlime(Inspector runObject) {
        var steps = new EnumMap<Step, StepInfo>(Step.class);
        Inspector detailsField = runObject.field(stepDetailsField);
        runObject.field(stepsField).traverse((ObjectTraverser) (step, status) -> {
            Step typedStep = stepOf(step);

            // For historical reasons are the step details stored in a separate JSON structure from the step statuses.
            Inspector stepDetailsField = detailsField.field(step);
            Inspector startTimeValue = stepDetailsField.field(startTimeField);
            Optional<Instant> startTime = SlimeUtils.optionalInstant(startTimeValue);

            steps.put(typedStep, new StepInfo(typedStep, stepStatusOf(status.asString()), startTime));
        });
        RunId id = new RunId(ApplicationId.fromSerializedForm(runObject.field(applicationField).asString()),
                             JobType.ofSerialized(runObject.field(jobTypeField).asString()),
                                 runObject.field(numberField).asLong());
        return new Run(id,
                       steps,
                       versionsFromSlime(runObject.field(versionsField), id),
                       runObject.field(isRedeploymentField).asBool(),
                       SlimeUtils.instant(runObject.field(startField)),
                       SlimeUtils.optionalInstant(runObject.field(endField)),
                       SlimeUtils.optionalInstant(runObject.field(sleepingUntilField)),
                       runStatusOf(runObject.field(statusField).asString()),
                       runObject.field(lastTestRecordField).asLong(),
                       Instant.EPOCH.plus(runObject.field(lastVespaLogTimestampField).asLong(), ChronoUnit.MICROS),
                       SlimeUtils.optionalInstant(runObject.field(noNodesDownSinceField)),
                       convergenceSummaryFrom(runObject.field(convergenceSummaryField)),
                       Optional.of(runObject.field(testerCertificateField))
                               .filter(Inspector::valid)
                               .map(certificate -> X509CertificateUtils.fromPem(certificate.asString())),
                       runObject.field(isDryRunField).valid() && runObject.field(isDryRunField).asBool(),
                       SlimeUtils.optionalString(runObject.field(reasonField)));
    }

    private Versions versionsFromSlime(Inspector versionsObject, RunId id) {
        Version targetPlatformVersion = Version.fromString(versionsObject.field(platformVersionField).asString());
        RevisionId targetRevision = revisionFrom(versionsObject, id);

        Optional<Version> sourcePlatformVersion = versionsObject.field(sourceField).valid()
                ? Optional.of(Version.fromString(versionsObject.field(sourceField).field(platformVersionField).asString()))
                : Optional.empty();
        Optional<RevisionId> sourceRevision = versionsObject.field(sourceField).valid()
                ? Optional.of(revisionFrom(versionsObject.field(sourceField), id))
                : Optional.empty();

        return new Versions(targetPlatformVersion, targetRevision, sourcePlatformVersion, sourceRevision);
    }

    private RevisionId revisionFrom(Inspector versionObject, RunId id) {
        long buildNumber = versionObject.field(buildField).asLong();
        boolean production =      versionObject.field(deployedDirectlyField).valid() // TODO jonmv: remove after migration
                             &&   buildNumber > 0
                             && ! versionObject.field(deployedDirectlyField).asBool();
        return production ? RevisionId.forProduction(buildNumber) : RevisionId.forDevelopment(buildNumber, id.job());
    }

    // Don't change this — introduce a separate array instead.
    private Optional<ConvergenceSummary> convergenceSummaryFrom(Inspector summaryArray) {
        if ( ! summaryArray.valid()) return Optional.empty();

        if (summaryArray.entries() != 12 && summaryArray.entries() != 13)
            throw new IllegalArgumentException("Convergence summary must have 13 entries");

        return Optional.of(new ConvergenceSummary(summaryArray.entry(0).asLong(),
                                                  summaryArray.entry(1).asLong(),
                                                  summaryArray.entry(2).asLong(),
                                                  summaryArray.entry(3).asLong(),
                                                  summaryArray.entry(4).asLong(),
                                                  summaryArray.entry(5).asLong(),
                                                  summaryArray.entry(6).asLong(),
                                                  summaryArray.entry(7).asLong(),
                                                  summaryArray.entry(8).asLong(),
                                                  summaryArray.entry(9).asLong(),
                                                  summaryArray.entry(10).asLong(),
                                                  summaryArray.entry(11).asLong(),
                                                  summaryArray.entry(12).asLong()));
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
        runObject.setString(jobTypeField, run.id().type().serialized());
        runObject.setBool(isRedeploymentField, run.isRedeployment());
        runObject.setLong(numberField, run.id().number());
        runObject.setLong(startField, run.start().toEpochMilli());
        run.end().ifPresent(end -> runObject.setLong(endField, end.toEpochMilli()));
        run.sleepUntil().ifPresent(end -> runObject.setLong(sleepingUntilField, end.toEpochMilli()));
        runObject.setString(statusField, valueOf(run.status()));
        runObject.setLong(lastTestRecordField, run.lastTestLogEntry());
        if (run.lastVespaLogTimestamp().isAfter(Instant.EPOCH)) runObject.setLong(lastVespaLogTimestampField, Instant.EPOCH.until(run.lastVespaLogTimestamp(), ChronoUnit.MICROS));
        run.noNodesDownSince().ifPresent(noNodesDownSince -> runObject.setLong(noNodesDownSinceField, noNodesDownSince.toEpochMilli()));
        run.convergenceSummary().ifPresent(convergenceSummary -> toSlime(convergenceSummary, runObject.setArray(convergenceSummaryField)));
        run.testerCertificate().ifPresent(certificate -> runObject.setString(testerCertificateField, X509CertificateUtils.toPem(certificate)));

        Cursor stepsObject = runObject.setObject(stepsField);
        run.steps().forEach((step, statusInfo) -> stepsObject.setString(valueOf(step), valueOf(statusInfo.status())));

        // For historical reasons are the step details stored in a different field from the step statuses.
        Cursor stepDetailsObject = runObject.setObject(stepDetailsField);
        run.steps().forEach((step, statusInfo) ->
                statusInfo.startTime().ifPresent(startTime ->
                        stepDetailsObject.setObject(valueOf(step)).setLong(startTimeField, valueOf(startTime))));

        Cursor versionsObject = runObject.setObject(versionsField);
        toSlime(run.versions().targetPlatform(), run.versions().targetRevision(), versionsObject);
        run.versions().sourcePlatform().ifPresent(sourcePlatformVersion -> {
            toSlime(sourcePlatformVersion,
                    run.versions().sourceRevision()
                       .orElseThrow(() -> new IllegalArgumentException("Source versions must be both present or absent.")),
                    versionsObject.setObject(sourceField));
        });
        runObject.setBool(isDryRunField, run.isDryRun());
        run.reason().ifPresent(reason -> runObject.setString(reasonField, reason));
    }

    private void toSlime(Version platformVersion, RevisionId revsion, Cursor versionsObject) {
        versionsObject.setString(platformVersionField, platformVersion.toString());
        versionsObject.setLong(buildField, revsion.number());
        versionsObject.setBool(deployedDirectlyField, ! revsion.isProduction());
    }

    // Don't change this - introduce a separate array with new values if needed.
    private void toSlime(ConvergenceSummary summary, Cursor summaryArray) {
        summaryArray.addLong(summary.nodes());
        summaryArray.addLong(summary.down());
        summaryArray.addLong(summary.upgradingOs());
        summaryArray.addLong(summary.upgradingFirmware());
        summaryArray.addLong(summary.needPlatformUpgrade());
        summaryArray.addLong(summary.upgradingPlatform());
        summaryArray.addLong(summary.needReboot());
        summaryArray.addLong(summary.rebooting());
        summaryArray.addLong(summary.needRestart());
        summaryArray.addLong(summary.restarting());
        summaryArray.addLong(summary.services());
        summaryArray.addLong(summary.needNewConfig());
        summaryArray.addLong(summary.retiring());
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
            case startStagingSetup  : return "startStagingSetup";
            case endStagingSetup    : return "endStagingSetup";
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
            case "startStagingSetup"  : return startStagingSetup;
            case "endStagingSetup"    : return endStagingSetup;
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

    static String valueOf(RunStatus status) {
        return switch (status) {
            case running                    -> "running";
            case nodeAllocationFailure      -> "nodeAllocationFailure";
            case endpointCertificateTimeout -> "endpointCertificateTimeout";
            case deploymentFailed           -> "deploymentFailed";
            case invalidApplication         -> "invalidApplication";
            case installationFailed         -> "installationFailed";
            case testFailure                -> "testFailure";
            case noTests                    -> "noTests";
            case error                      -> "error";
            case success                    -> "success";
            case aborted                    -> "aborted";
            case cancelled                  -> "cancelled";
            case reset                      -> "reset";
            case quotaExceeded              -> "quotaExceeded";
        };
    }

    static RunStatus runStatusOf(String status) {
        return switch (status) {
            case "running"                    -> running;
            case "nodeAllocationFailure"      -> nodeAllocationFailure;
            case "endpointCertificateTimeout" -> endpointCertificateTimeout;
            case "deploymentFailed"           -> deploymentFailed;
            case "invalidApplication"         -> invalidApplication;
            case "installationFailed"         -> installationFailed;
            case "noTests"                    -> noTests;
            case "testFailure"                -> testFailure;
            case "error"                      -> error;
            case "success"                    -> success;
            case "aborted"                    -> aborted;
            case "cancelled"                  -> cancelled;
            case "reset"                      -> reset;
            case "quotaExceeded"              -> quotaExceeded;
            default -> throw new IllegalArgumentException("No run status defined by '" + status + "'!");
        };
    }

}
