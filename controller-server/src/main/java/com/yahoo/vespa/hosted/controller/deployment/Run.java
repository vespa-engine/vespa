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

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.success;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
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
    private final Map<Step, Step.Status> steps;
    private final Versions versions;
    private final Instant start;
    private final Optional<Instant> end;
    private final RunStatus status;
    private final long lastTestRecord;
    private final Instant lastVespaLogTimestamp;
    private final Optional<X509Certificate> testerCertificate;

    // For deserialisation only -- do not use!
    public Run(RunId id, Map<Step, Step.Status> steps, Versions versions, Instant start,
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
        EnumMap<Step, Step.Status> steps = new EnumMap<>(Step.class);
        JobProfile.of(id.type()).steps().forEach(step -> steps.put(step, unfinished));
        return new Run(id, steps, requireNonNull(versions), requireNonNull(now), Optional.empty(), running,
                       -1, Instant.EPOCH, Optional.empty());
    }

    /** Returns a new Run with the new status, and with the status of the given, completed step set accordingly. */
    public Run with(RunStatus status, LockedStep step) {
        requireActive();
        if (steps.get(step.get()) != unfinished)
            throw new IllegalStateException("Step '" + step.get() + "' can't be set to '" + status + "'" +
                                     " -- it already completed with status '" + steps.get(step.get()) + "'!");

        EnumMap<Step, Step.Status> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), Step.Status.of(status));
        return new Run(id, steps, versions, start, end, this.status == running ? status : this.status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run finished(Instant now) {
        requireActive();
        return new Run(id, new EnumMap<>(steps), versions, start, Optional.of(now), status == running ? success : status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run aborted() {
        requireActive();
        return new Run(id, new EnumMap<>(steps), versions, start, end, aborted,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run with(long lastTestRecord) {
        requireActive();
        return new Run(id, new EnumMap<>(steps), versions, start, end, status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run with(Instant lastVespaLogTimestamp) {
        requireActive();
        return new Run(id, new EnumMap<>(steps), versions, start, end, status,
                       lastTestRecord, lastVespaLogTimestamp, testerCertificate);
    }

    public Run with(X509Certificate testerCertificate) {
        requireActive();
        return new Run(id, new EnumMap<>(steps), versions, start, end, status,
                       lastTestRecord, lastVespaLogTimestamp, Optional.of(testerCertificate));
    }

    /** Returns the id of this run. */
    public RunId id() {
        return id;
    }

    /** Returns an unmodifiable view of the status of all steps in this run. */
    public Map<Step, Step.Status> steps() {
        return steps;
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
                                         .filter(entry ->    entry.getValue() == unfinished
                                                          && entry.getKey().prerequisites().stream()
                                                                  .allMatch(step ->    steps.get(step) == null
                                                                                    || steps.get(step) == succeeded))
                                         .map(Map.Entry::getKey)
                                         .iterator());
    }

    /** Returns the list of not-yet-run run-always steps whose run-always prerequisites have all run. */
    private List<Step> forcedSteps() {
        return ImmutableList.copyOf(steps.entrySet().stream()
                                         .filter(entry ->    entry.getValue() == unfinished
                                                          && JobProfile.of(id.type()).alwaysRun().contains(entry.getKey())
                                                          && entry.getKey().prerequisites().stream()
                                                                  .filter(JobProfile.of(id.type()).alwaysRun()::contains)
                                                                  .allMatch(step -> steps.get(step) != unfinished))
                                         .map(Map.Entry::getKey)
                                         .iterator());
    }

    private void requireActive() {
        if (hasEnded())
            throw new IllegalStateException("This run ended at " + end.get() + " -- it can't be further modified!");
    }

}
