// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.nodeAllocationFailure;

/**
 * A list of deployment jobs that can be filtered in various ways.
 *
 * @author jonmv
 */
public class JobList extends AbstractFilteringList<JobStatus, JobList> {

    private JobList(Collection<? extends JobStatus> jobs, boolean negate) {
        super(jobs, negate, JobList::new);
    }

    // ----------------------------------- Factories

    public static JobList from(Collection<? extends JobStatus> jobs) {
        return new JobList(jobs, false);
    }

    // ----------------------------------- Basic filters

    /** Returns the status of the job of the given type, if it is contained in this. */
    public Optional<JobStatus> get(JobId id) {
        return asList().stream().filter(job -> job.id().equals(id)).findAny();
    }

    /** Returns the subset of jobs which are currently upgrading */
    public JobList upgrading() {
        return matching(job ->    job.isRunning()
                               && job.lastSuccess().isPresent()
                               && job.lastSuccess().get().versions().targetPlatform().isBefore(job.lastTriggered().get().versions().targetPlatform()));
    }

    /** Returns the subset of jobs which are currently failing */
    public JobList failing() {
        return matching(job -> job.lastCompleted().isPresent() && ! job.isSuccess());
    }

    /** Returns the subset of jobs which are currently failing, not out of test capacity, and not aborted. */
    public JobList failingHard() {
        return failing().not().outOfTestCapacity().not().withStatus(aborted);
    }

    public JobList outOfTestCapacity() {
        return matching(job -> job.isNodeAllocationFailure() && job.id().type().environment().isTest());
    }

    public JobList running() {
        return matching(job -> job.isRunning());
    }

    /** Returns the subset of jobs which must be failing due to an application change */
    public JobList failingApplicationChange() {
        return matching(JobList::failingApplicationChange);
    }

    /** Returns the subset of jobs which are failing because of an application change, and have been since the threshold, on the given revision. */
    public JobList failingWithBrokenRevisionSince(RevisionId broken, Instant threshold) {
        return failingApplicationChange().matching(job -> job.runs().values().stream()
                                                             .anyMatch(run ->    run.versions().targetRevision().equals(broken)
                                                                              && run.hasFailed()
                                                                              && run.start().isBefore(threshold)));
    }

    /** Returns the subset of jobs which are failing with the given run status. */
    public JobList withStatus(RunStatus status) {
        return matching(job -> job.lastStatus().map(status::equals).orElse(false));
    }

    /** Returns the subset of jobs of the given type -- most useful when negated. */
    public JobList type(Collection<? extends JobType> types) {
        return matching(job -> types.contains(job.id().type()));
    }

    /** Returns the subset of jobs of the given type -- most useful when negated. */
    public JobList type(JobType... types) {
        return type(List.of(types));
    }

    /** Returns the subset of jobs run for the given instance. */
    public JobList instance(InstanceName... instances) {
        return instance(Set.of(instances));
    }

    /** Returns the subset of jobs run for the given instance. */
    public JobList instance(Collection<InstanceName> instances) {
        return matching(job -> instances.contains(job.id().application().instance()));
    }

    /** Returns the subset of jobs of which are production jobs. */
    public JobList production() {
        return matching(job -> job.id().type().isProduction());
    }

    /** Returns the jobs with any runs failing with non-out-of-test-capacity on the given versions — targets only for system test, everything present otherwise. */
    public JobList failingHardOn(Versions versions) {
        return matching(job -> ! RunList.from(job)
                                        .on(versions)
                                        .matching(Run::hasFailed)
                                        .not().matching(run -> run.status() == nodeAllocationFailure && run.id().type().environment().isTest())
                                        .isEmpty());
    }

    /** Returns the jobs with any runs matching the given versions — targets only for system test, everything present otherwise. */
    public JobList triggeredOn(Versions versions) {
        return matching(job -> ! RunList.from(job).on(versions).isEmpty());
    }

    /** Returns the jobs with successful runs matching the given versions — targets only for system test, everything present otherwise. */
    public JobList successOn(JobType type, Versions versions) {
        return matching(job ->      job.id().type().equals(type)
                               && ! RunList.from(job)
                                           .matching(run -> run.hasSucceeded() && run.id().type().zone().equals(type.zone()))
                                           .on(versions)
                                           .isEmpty());
    }

    // ----------------------------------- JobRun filtering

    /** Returns the list in a state where the next filter is for the lastTriggered run type */
    public RunFilter lastTriggered() {
        return new RunFilter(JobStatus::lastTriggered);
    }

    /** Returns the list in a state where the next filter is for the lastCompleted run type */
    public RunFilter lastCompleted() {
        return new RunFilter(JobStatus::lastCompleted);
    }

    /** Returns the list in a state where the next filter is for the lastSuccess run type */
    public RunFilter lastSuccess() {
        return new RunFilter(JobStatus::lastSuccess);
    }

    /** Returns the list in a state where the next filter is for the firstFailing run type */
    public RunFilter firstFailing() {
        return new RunFilter(JobStatus::firstFailing);
    }

    /** Allows sub-filters for runs of the indicated kind */
    public class RunFilter {

        private final Function<JobStatus, Optional<Run>> which;

        private RunFilter(Function<JobStatus, Optional<Run>> which) {
            this.which = which;
        }

        /** Returns the subset of jobs where the run of the indicated type exists */
        public JobList present() {
            return matching(run -> true);
        }

        /** Returns the runs of the indicated kind, mapped by the given function, as a list. */
        public <OtherType> List<OtherType> mapToList(Function<? super Run, OtherType> mapper) {
            return present().mapToList(which.andThen(Optional::get).andThen(mapper));
        }

        /** Returns the runs of the indicated kind. */
        public List<Run> asList() {
            return mapToList(Function.identity());
        }

        /** Returns the subset of jobs where the run of the indicated type ended no later than the given instant */
        public JobList endedNoLaterThan(Instant threshold) {
            return matching(run -> ! run.end().orElse(Instant.MAX).isAfter(threshold));
        }

        /** Returns the subset of jobs where the run of the indicated type was on the given version */
        public JobList on(RevisionId revision) {
            return matching(run -> run.versions().targetRevision().equals(revision));
        }

        /** Returns the subset of jobs where the run of the indicated type was on the given version */
        public JobList on(Version version) {
            return matching(run -> run.versions().targetPlatform().equals(version));
        }

        /** Transforms the JobRun condition to a JobStatus condition, by considering only the JobRun mapped by which, and executes */
        private JobList matching(Predicate<Run> condition) {
            return JobList.this.matching(job -> which.apply(job).filter(condition).isPresent());
        }

    }

    // ----------------------------------- Internal helpers

    private static boolean failingApplicationChange(JobStatus job) {
        if (job.isSuccess()) return false;
        if (job.lastSuccess().isEmpty()) return true; // An application which never succeeded is surely bad.
        if ( ! job.firstFailing().get().versions().targetPlatform().equals(job.lastSuccess().get().versions().targetPlatform())) return false; // Version change may be to blame.
        return ! job.firstFailing().get().versions().targetRevision().equals(job.lastSuccess().get().versions().targetRevision()); // Return whether there is an application change.
    }

}

