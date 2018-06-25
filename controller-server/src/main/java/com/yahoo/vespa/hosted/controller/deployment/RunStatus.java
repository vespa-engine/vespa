package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static java.util.Objects.requireNonNull;

/**
 * Immutable class containing status information for a deployment job run by an {@link InternalBuildService}.
 *
 * @author jonmv
 */
public class RunStatus {

    private final RunId id;
    private final Map<Step, Step.Status> status;
    private final Instant start;
    private final Instant end;

    RunStatus(RunId id, Map<Step, Step.Status> status, Instant start, Instant end) {
        this.id = id;
        this.status = status;
        this.start = start;
        this.end = end;
    }

    public static RunStatus initial(RunId id, Instant now) {
        Map<Step, Step.Status> status = new EnumMap<>(Step.class);
        JobProfile.of(id.type()).steps().forEach(step -> status.put(step, unfinished));
        return new RunStatus(requireNonNull(id), status, requireNonNull(now), null);
    }

    public RunStatus with(Step.Status update, LockedStep step) {
        RunStatus run = new RunStatus(id, status, start, end);
        run.status.put(step.get(), update);
        return run;
    }

    public RunStatus with(Instant now) {
        return new RunStatus(id, status, start, now);
    }

    /** Returns the id of this run. */
    public RunId id() {
        return id;
    }

    /** Returns an unmodifiable view of the status of all steps in this run. */
    public Map<Step, Step.Status> status() {
        return Collections.unmodifiableMap(status);
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
        return Optional.ofNullable(end);
    }

}
