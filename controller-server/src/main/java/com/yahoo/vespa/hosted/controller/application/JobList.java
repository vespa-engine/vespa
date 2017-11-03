package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.stream.Stream;

public class JobList {

    private final ImmutableList<JobStatus> list;

    private JobList(Iterable<JobStatus> jobs) {
        this.list = ImmutableList.copyOf(jobs);
    }

    // ----------------------------------- Factories

    public static JobList from(Iterable<JobStatus> jobs) {
        return new JobList(jobs);
    }

    // ----------------------------------- Accessors

    /** Returns the jobstatuses in this as an immutable list */
    public List<JobStatus> asList() { return list; }

    public boolean isEmpty() { return list.isEmpty(); }

    public int size() { return list.size(); }

    // ----------------------------------- Filters

    /** Returns the subset of applications which are currently upgrading (to any version) */
    public JobList failing() {
        return asList(list.stream().filter(job -> ! job.isSuccess()));
    }

    public JobList failingApplicationChange(JobStatus job) {
        return asList(list.stream().filter(job -> failingApplicationChange(job)));
    }

    // ----------------------------------- Internal helpers

    private static boolean failingApplicationChange(JobStatus job) {
        if (   job.isSuccess()) return false;
        if ( ! job.lastSuccess().isPresent()) return true; // An application which never succeeded is surely bad.
        if ( ! job.lastSuccess().get().revision().isPresent()) return true; // Indicates the component job, which is always an application change.
        if ( ! job.firstFailing().get().version().equals(job.lastSuccess().get().version())) return false; // Version change may be to blame.
        return ! job.firstFailing().get().revision().equals(job.lastSuccess().get().revision()); // Return whether there is an application change.
    }

    private static JobList asList(Stream<JobStatus> jobs) {
        return new JobList(jobs::iterator);
    }

}

