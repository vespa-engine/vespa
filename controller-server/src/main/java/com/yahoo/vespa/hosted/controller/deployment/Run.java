// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
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
    private final Instant start;
    private final Optional<Instant> end;
    private final RunStatus status;
    private final long lastTestRecord;
    private final Instant lastVespaLogTimestamp;
    private final Optional<X509Certificate> testerCertificate;

    // For deserialisation only -- do not use!
    public Run(RunId id, Map<Step, StepInfo> steps, Versions versions, Instant start,
               Optional<Instant> end, RunStatus status, long lastTestRecord, Instant lastVespaLogTimestamp,
               Optional<X509Certificate> testerCertificate) {
        this.id = id;
        this.steps = Collections.unmodifiableMap(new EnumMap<>(steps));
        this.versions = versions;
        this.start = start;
        this.end = end;
        this.status = status;
        this.lastTestRecord = lastTestRecord;
        this.lastVespaLogTimestamp = lastVespaLogTimestamp;
        this.testerCertificate = testerCertificate;
    }

    public static Run initial(RunId id, Versions versions, Instant now) {
        EnumMap<Step, StepInfo> steps = new EnumMap<>(Step.class);
        JobProfile.of(id.type()).steps().forEach(step -> steps.put(step, StepInfo.initial(step)));
        return new Run(id, steps, requireNonNull(versions), requireNonNull(now), Optional.empty(), running,
                       -1, Instant.EPOCH, Optional.empty());
    }

    /** Returns a new Run with the status of the given completed step set accordingly. */
    public Run with(RunStatus status, LockedStep step) {
        requireActive();
        StepInfo stepInfo = steps.get(step.get());
        if (stepInfo == null || stepInfo.status() != unfinished)
            throw new IllegalStateException("Step '" + step.get() + "' can't be set to '" + status + "'" +
                                     " -- it already completed with status '" + stepInfo.status() + "'!");

        EnumMap<Step, StepInfo> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), stepInfo.with(Step.Status.of(status)));
        return new Run(id, steps, versions, start, end, this.status == running ? status : this.status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    /** Returns a new Run with a new start time*/
    public Run with(Instant startTime, LockedStep step) {
        requireActive();
        StepInfo stepInfo = steps.get(step.get());
        if (stepInfo == null || stepInfo.status() != unfinished)
            throw new IllegalStateException("Unable to set start timestamp of step " + step.get() +
                    ": it has already completed with status " + stepInfo.status() + "!");

        EnumMap<Step, StepInfo> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), stepInfo.with(startTime));

        return new Run(id, steps, versions, start, end, status, lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run finished(Instant now) {
        requireActive();
        return new Run(id, steps, versions, start, Optional.of(now), status == running ? success : status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run aborted() {
        requireActive();
        return new Run(id, steps, versions, start, end, aborted,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run with(long lastTestRecord) {
        requireActive();
        return new Run(id, steps, versions, start, end, status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run with(Instant lastVespaLogTimestamp) {
        requireActive();
        return new Run(id, steps, versions, start, end, status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run with(X509Certificate testerCertificate) {
        requireActive();
        return new Run(id, steps, versions, start, end, status,
                       lastTestRecord, lastVespaLogTimestamp, Optional.of(testerCertificate));
    }

    /** Returns the id of this run. */
    public RunId id() {
        return id;
    }

    /** Whether this run contains this step. */
    public boolean hasStep(Step step) {
        return steps.containsKey(step);
    }

    /** Returns info on step. */
    public Optional<StepInfo> stepInfo(Step step) {
        return Optional.ofNullable(steps.get(step));
    }

    /** Returns status of step. */
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

    /** Returns whether the run has failed, and should switch to its run-always steps. */
    public boolean hasFailed() {
        return status != running && status != success;
    }

    /** Returns whether the run has ended, i.e., has become inactive, and can no longer be updated. */
    public boolean hasEnded() {
        return end.isPresent();
    }

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

    /** Returns the tester certificate for this run, or empty. */
    public Optional<X509Certificate> testerCertificate() {
        return testerCertificate;
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
        return ImmutableList.copyOf(steps.entrySet().stream()
                                         .filter(entry ->    entry.getValue().status() == unfinished
                                                          && entry.getKey().prerequisites().stream()
                                                                  .allMatch(step ->    steps.get(step) == null
                                                                                    || steps.get(step).status() == succeeded))
                                         .map(Map.Entry::getKey)
                                         .iterator());
    }

    /** Returns the list of not-yet-run run-always steps whose run-always prerequisites have all run. */
    private List<Step> forcedSteps() {
        return ImmutableList.copyOf(steps.entrySet().stream()
                                         .filter(entry ->    entry.getValue().status() == unfinished
                                                          && JobProfile.of(id.type()).alwaysRun().contains(entry.getKey())
                                                          && entry.getKey().prerequisites().stream()
                                                                  .filter(JobProfile.of(id.type()).alwaysRun()::contains)
                                                                  .allMatch(step ->    steps.get(step) == null
                                                                                    || steps.get(step).status() != unfinished))
                                         .map(Map.Entry::getKey)
                                         .iterator());
    }

    private void requireActive() {
        if (hasEnded())
            throw new IllegalStateException("This run ended at " + end.get() + " -- it can't be further modified!");
    }

}
