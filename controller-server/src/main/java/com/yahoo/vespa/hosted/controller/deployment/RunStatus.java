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
 * Immutable class containing status information for a deployment job run by an {@link InternalBuildService}.
 *
 * @author jonmv
 */
public class RunStatus {

    private final RunId id;
    private final Map<Step, Step.Status> steps;
    private final Instant start;
    private final Optional<Instant> end;
    // TODO jvenstad: Add a Versions object and a reason String. Requires shortcutting of triggering of these runs.

    // For deserialisation only -- do not use!
    public RunStatus(RunId id, Map<Step, Step.Status> steps, Instant start, Optional<Instant> end) {
        this.id = id;
        this.steps = Collections.unmodifiableMap(new EnumMap<>(steps));
        this.start = start;
        this.end = end;
    }

    public static RunStatus initial(RunId id, Instant now) {
        EnumMap<Step, Step.Status> steps = new EnumMap<>(Step.class);
        JobProfile.of(id.type()).steps().forEach(step -> steps.put(step, unfinished));
        return new RunStatus(id, steps, requireNonNull(now), Optional.empty());
    }

    public RunStatus with(Step.Status status, LockedStep step) {
        if (end.isPresent())
            throw new AssertionError("This step ended at " + end.get() + " -- it can't be further modified!");

        EnumMap<Step, Step.Status> steps = new EnumMap<>(this.steps);
        steps.put(step.get(), requireNonNull(status));
        return new RunStatus(id, steps, start, end);
    }

    public RunStatus finish(Instant now) {
        if (end.isPresent())
            throw new AssertionError("This step ended at " + end.get() + " -- it can't be ended again!");

        return new RunStatus(id, new EnumMap<>(steps), start, Optional.of(now));
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
        // TODO jvenstad: To implement, or not ... If so, base on status.
        throw new AssertionError();
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
        return steps.values().contains(failed);
    }

    /** Returns whether the run has ended, i.e., has become inactive, and can no longer be updated. */
    public boolean hasEnded() {
        return end.isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof RunStatus)) return false;

        RunStatus status = (RunStatus) o;

        return id.equals(status.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RunStatus{" +
               "id=" + id +
               ", start=" + start +
               ", end=" + end +
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
