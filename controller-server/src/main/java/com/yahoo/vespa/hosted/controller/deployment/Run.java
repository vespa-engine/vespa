// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.noTests;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.success;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static java.util.Objects.requireNonNull;

/**
 * Immutable class containing status information for a deployment job run by a {@link JobController}.
 *
 * @author jonmv
 */
public class Run {

    private final RunId id;
    private final Map<Step, StepInfo> steps;
    private final Versions versions;
    private final boolean isRedeployment;
    private final Instant start;
    private final Optional<Instant> end;
    private final Optional<Instant> sleepUntil;
    private final RunStatus status;
    private final long lastTestRecord;
    private final Instant lastVespaLogTimestamp;
    private final Optional<Instant> noNodesDownSince;
    private final Optional<ConvergenceSummary> convergenceSummary;
    private final Optional<X509Certificate> testerCertificate;
    private final boolean dryRun;
    private final Optional<String> reason;

    // For deserialisation only -- do not use!
    public Run(RunId id, Map<Step, StepInfo> steps, Versions versions, boolean isRedeployment, Instant start, Optional<Instant> end,
               Optional<Instant> sleepUntil, RunStatus status, long lastTestRecord, Instant lastVespaLogTimestamp,
               Optional<Instant> noNodesDownSince, Optional<ConvergenceSummary> convergenceSummary,
               Optional<X509Certificate> testerCertificate, boolean dryRun, Optional<String> reason) {
        this.id = id;
        this.steps = Collections.unmodifiableMap(new EnumMap<>(steps));
        this.versions = versions;
        this.isRedeployment = isRedeployment;
        this.start = start;
        this.end = end;
        this.sleepUntil = sleepUntil;
        this.status = status;
        this.lastTestRecord = lastTestRecord;
        this.lastVespaLogTimestamp = lastVespaLogTimestamp;
        this.noNodesDownSince = noNodesDownSince;
        this.convergenceSummary = convergenceSummary;
        this.testerCertificate = testerCertificate;
        this.dryRun = dryRun;
        this.reason = reason;
    }

    public static Run initial(RunId id, Versions versions, boolean isRedeployment, Instant now, JobProfile profile, Optional<String> triggeredBy) {
        EnumMap<Step, StepInfo> steps = new EnumMap<>(Step.class);
        profile.steps().forEach(step -> steps.put(step, StepInfo.initial(step)));
        return new Run(id, steps, requireNonNull(versions), isRedeployment, requireNonNull(now), Optional.empty(),
                       Optional.empty(), running, -1, Instant.EPOCH, Optional.empty(), Optional.empty(),
                       Optional.empty(), profile == JobProfile.developmentDryRun, triggeredBy);
    }

    /** Returns a new Run with the status of the given completed step set accordingly. */
    public Run with(RunStatus status, LockedStep step) {
        requireActive();
        StepInfo stepInfo = getRequiredStepInfo(step.get());
        if (stepInfo.status() != unfinished)
            throw new IllegalStateException("Step '" + step.get() + "' can't be set to '" + status + "'" +
                                     " -- it already completed with status '" + stepInfo.status() + "'!");

        EnumMap<Step, StepInfo> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), stepInfo.with(Step.Status.of(status)));
        RunStatus newStatus = hasFailed() || status == running ? this.status : status;
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, newStatus, lastTestRecord,
                       lastVespaLogTimestamp, noNodesDownSince, convergenceSummary, testerCertificate, dryRun, reason);
    }

    /** Returns a new Run with a new start time*/
    public Run with(Instant startTime, LockedStep step) {
        requireActive();
        StepInfo stepInfo = getRequiredStepInfo(step.get());
        if (stepInfo.status() != unfinished)
            throw new IllegalStateException("Unable to set start timestamp of step " + step.get() +
                    ": it has already completed with status " + stepInfo.status() + "!");

        EnumMap<Step, StepInfo> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), stepInfo.with(startTime));

        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, status, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, convergenceSummary, testerCertificate, dryRun, reason);
    }

    public Run finished(Instant now) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, Optional.of(now), sleepUntil, status == running ? success : status,
                       lastTestRecord, lastVespaLogTimestamp, noNodesDownSince, convergenceSummary, Optional.empty(), dryRun, reason);
    }

    public Run aborted() {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, aborted, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, convergenceSummary, testerCertificate, dryRun, reason);
    }

    public Run reset() {
        requireActive();
        Map<Step, StepInfo> reset = new EnumMap<>(steps);
        reset.replaceAll((step, __) -> StepInfo.initial(step));
        return new Run(id, reset, versions, isRedeployment, start, end, sleepUntil, running, -1, lastVespaLogTimestamp,
                       Optional.empty(), Optional.empty(), testerCertificate, dryRun, reason);
    }

    public Run with(long lastTestRecord) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, status, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, convergenceSummary, testerCertificate, dryRun, reason);
    }

    public Run with(Instant lastVespaLogTimestamp) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, status, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, convergenceSummary, testerCertificate, dryRun, reason);
    }

    public Run noNodesDownSince(Instant noNodesDownSince) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, status, lastTestRecord, lastVespaLogTimestamp,
                       Optional.ofNullable(noNodesDownSince), convergenceSummary, testerCertificate, dryRun, reason);
    }

    public Run withSummary(ConvergenceSummary convergenceSummary) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, status, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, Optional.ofNullable(convergenceSummary), testerCertificate, dryRun, reason);
    }

    public Run with(X509Certificate testerCertificate) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, sleepUntil, status, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, convergenceSummary, Optional.of(testerCertificate), dryRun, reason);
    }

    public Run sleepingUntil(Instant instant) {
        requireActive();
        return new Run(id, steps, versions, isRedeployment, start, end, Optional.of(instant), status, lastTestRecord, lastVespaLogTimestamp,
                       noNodesDownSince, convergenceSummary, testerCertificate, dryRun, reason);
    }

    /** Returns the id of this run. */
    public RunId id() {
        return id;
    }

    /** Whether this run contains this step. */
    public boolean hasStep(Step step) {
        return steps.containsKey(step);
    }

    /** Returns info on step, or empty if the given step is not a part of this run. */
    public Optional<StepInfo> stepInfo(Step step) {
        return Optional.ofNullable(steps.get(step));
    }

    private StepInfo getRequiredStepInfo(Step step) {
        return stepInfo(step).orElseThrow(() -> new IllegalArgumentException("There is no such step " + step + " for run " + id));
    }

    /** Returns status of step, or empty if the given step is not a part of this run. */
    public Optional<Step.Status> stepStatus(Step step) {
        return stepInfo(step).map(StepInfo::status);
    }

    /** Returns an unmodifiable view of all step information in this run. */
    public Map<Step, StepInfo> steps() {
        return steps;
    }

    /** Returns an unmodifiable view of the status of all steps in this run. */
    public Map<Step, Step.Status> stepStatuses() {
        return Collections.unmodifiableMap(steps.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().status())));
    }

    public RunStatus status() {
        return status;
    }

    /** Returns the instant at which this run began. */
    public Instant start() {
        return start;
    }

    /** Returns the instant at which this run ended, if it has. */
    public Optional<Instant> end() {
        return end;
    }

    /** Returns the instant until which this should sleep. */
    public Optional<Instant> sleepUntil() {
        return sleepUntil;
    }

    /** Returns whether the run has failed, and should switch to its run-always steps. */
    public boolean hasFailed() {
        return status != running && status != success && status != noTests;
    }

    /** Returns whether the run has ended, i.e., has become inactive, and can no longer be updated. */
    public boolean hasEnded() {
        return end.isPresent();
    }

    public boolean hasSucceeded() { return hasEnded() && ! hasFailed(); }

    /** Returns the target, and possibly source, versions for this run. */
    public Versions versions() {
        return versions;
    }

    /** Returns the sequence id of the last test record received from the tester, for the test logs of this run. */
    public long lastTestLogEntry() {
        return lastTestRecord;
    }

    /** Returns the timestamp of the last Vespa log record fetched and stored for this run. */
    public Instant lastVespaLogTimestamp() {
        return lastVespaLogTimestamp;
    }

    /** Returns since when no nodes have been allowed to be down. */
    public Optional<Instant> noNodesDownSince() {
        return noNodesDownSince;
    }

    /** Returns a summary of convergence status during an application deployment — staging or upgrade. */
    public Optional<ConvergenceSummary> convergenceSummary() {
        return convergenceSummary;
    }

    /** Returns the tester certificate for this run, or empty. */
    public Optional<X509Certificate> testerCertificate() {
        return testerCertificate;
    }

    /** Whether this is a automatic redeployment. */
    public boolean isRedeployment() {
        return isRedeployment;
    }

    /** Whether this is a dry run deployment. */
    public boolean isDryRun() { return dryRun; }

    /** The specific reason for triggering this run, if any. This should be empty for jobs triggered bvy deployment orchestration. */
    public Optional<String> reason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Run)) return false;

        Run run = (Run) o;

        return id.equals(run.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RunStatus{" +
               "id=" + id +
               ", versions=" + versions +
               ", start=" + start +
               ", end=" + end +
               ", status=" + status +
               '}';
    }

    /** Returns the list of steps to run for this job right now, depending on whether the job has failed. */
    public List<Step> readySteps() {
        return hasFailed() ? forcedSteps() : normalSteps();
    }

    /** Returns the list of unfinished steps whose prerequisites have all succeeded. */
    private List<Step> normalSteps() {
        return steps.entrySet().stream()
                    .filter(entry -> entry.getValue().status() == unfinished
                                     && entry.getKey().prerequisites().stream()
                                             .allMatch(step ->    steps.get(step) == null
                                                               || steps.get(step).status() == succeeded))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableList());
    }

    /** Returns the list of not-yet-run run-always steps whose run-always prerequisites have all run. */
    private List<Step> forcedSteps() {
        return steps.entrySet().stream()
                    .filter(entry -> entry.getValue().status() == unfinished
                                     && entry.getKey().alwaysRun()
                                     && entry.getKey().prerequisites().stream()
                                             .filter(Step::alwaysRun)
                                             .allMatch(step ->    steps.get(step) == null
                                                               || steps.get(step).status() != unfinished))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableList());
    }

    private void requireActive() {
        if (hasEnded())
            throw new IllegalStateException("This run ended at " + end.get() + " -- it can't be further modified!");
    }

}
