package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final boolean aborted;

    // For deserialisation only -- do not use!
    public Run(RunId id, Map<Step, Step.Status> steps, Versions versions,
               Instant start, Optional<Instant> end, boolean aborted) {
        this.id = id;
        this.steps = Collections.unmodifiableMap(new EnumMap<>(steps));
        this.versions = versions;
        this.start = start;
        this.end = end;
        this.aborted = aborted;
    }

    public static Run initial(RunId id, Versions versions, Instant now) {
        EnumMap<Step, Step.Status> steps = new EnumMap<>(Step.class);
        JobProfile.of(id.type()).steps().forEach(step -> steps.put(step, unfinished));
        return new Run(id, steps, requireNonNull(versions), requireNonNull(now), Optional.empty(), false);
    }

    public Run with(Step.Status status, LockedStep step) {
        if (hasEnded())
            throw new AssertionError("This step ended at " + end.get() + " -- it can't be further modified!");

        EnumMap<Step, Step.Status> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), requireNonNull(status));
        return new Run(id, steps, versions, start, end, aborted);
    }

    public Run finished(Instant now) {
        if (hasEnded())
            throw new AssertionError("This step ended at " + end.get() + " -- it can't be ended again!");

        return new Run(id, new EnumMap<>(steps), versions, start, Optional.of(now), aborted);
    }

    public Run aborted() {
        if (hasEnded())
            throw new AssertionError("This step ended at " + end.get() + " -- it can't be aborted now!");

        return new Run(id, new EnumMap<>(steps), versions, start, end, true);
    }

    /** Returns the id of this run. */
    public RunId id() {
        return id;
    }

    /** Returns an unmodifiable view of the status of all steps in this run. */
    public Map<Step, Step.Status> steps() {
        return steps;
    }

    /** Returns the final result of this run, if it has ended. */
    public Optional<RunResult> result() {

        // No result of not finished yet
        if ( ! hasEnded()) return Optional.empty();

        // If any steps has failed - then we need to figure out what - for now return fixed error result
        if (hasFailed()) return Optional.of(RunResult.testError);

        return Optional.of(RunResult.success);
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
        return aborted || steps.values().contains(failed);
    }

    /** Returns whether the run has been forcefully aborted. */
    public boolean isAborted() {
        return aborted;
    }

    /** Returns whether the run has ended, i.e., has become inactive, and can no longer be updated. */
    public boolean hasEnded() {
        return end.isPresent();
    }

    /** Returns the target, and possibly source, versions for this run. */
    public Versions versions() {
        return versions;
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
               ", aborted=" + aborted +
               ", steps=" + steps +
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

    /** Returns the list of not-yet-succeeded run-always steps whose run-always prerequisites have all succeeded. */
    private List<Step> forcedSteps() {
        return ImmutableList.copyOf(steps.entrySet().stream()
                                         .filter(entry ->    entry.getValue() != succeeded
                                                          && JobProfile.of(id.type()).alwaysRun().contains(entry.getKey())
                                                          && entry.getKey().prerequisites().stream()
                                                                  .filter(JobProfile.of(id.type()).alwaysRun()::contains)
                                                                  .allMatch(step ->    steps.get(step) == null
                                                                                    || steps.get(step) == succeeded))
                                         .map(Map.Entry::getKey)
                                         .iterator());
    }

}
