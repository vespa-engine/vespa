package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.pending;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Contains state information for a deployment job run by an {@link InternalBuildService}.
 *
 * Immutable.
 *
 * @author jonmv
 */
public class RunStatus {

    private final RunId id;
    private final Map<Step, Step.Status> status;
    private final Set<Step> alwaysRun;
    private final RunResult result;
    private final Instant start;
    private final Instant end;

    RunStatus(RunId id, Map<Step, Step.Status> status, Set<Step> alwaysRun, RunResult result, Instant start, Instant end) {
        this.id = id;
        this.status = status;
        this.alwaysRun = alwaysRun;
        this.result = result;
        this.start = start;
        this.end = end;
    }

    public static RunStatus initial(RunId id, Set<Step> runWhileSuccess, Set<Step> alwaysRun, Instant now) {
        ImmutableMap.Builder<Step, Step.Status> status = ImmutableMap.builder();
        runWhileSuccess.forEach(step -> status.put(step, pending));
        alwaysRun.forEach(step -> status.put(step, pending));
        return new RunStatus(requireNonNull(id), status.build(), alwaysRun, null, requireNonNull(now), null);
    }

    public RunStatus with(Step.Status update, Step step) {
        return new RunStatus(id, ImmutableMap.<Step, Step.Status>builder().putAll(status).put(step, update).build(), alwaysRun, result, start, end);
    }

    /** Returns the id of this run. */
    public RunId id() {
        return id;
    }

    /** Returns the status of all steps in this run. */
    public Map<Step, Step.Status> status() {
        return status;
    }

    /** Returns the final result of this run, if it has ended. */
    public Optional<RunResult> result() {
        return Optional.ofNullable(result);
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
