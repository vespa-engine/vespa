package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import java.util.Objects;

import static ai.vespa.validation.Validation.requireAtLeast;

/**
 * ID of a revision of an application package. This is the build number, and whether it was submitted for production deployment.
 *
 * @author jonmv
 */
public class RevisionId implements Comparable<RevisionId> {

    private final long number;
    private final JobId job;

    private RevisionId(long number, JobId job) {
        this.number = number;
        this.job = job;
    }

    public static RevisionId forProduction(long number) {
        return new RevisionId(requireAtLeast(number, "build number", 1L), null);
    }

    public static RevisionId forDevelopment(long number, JobId job) {
        return new RevisionId(requireAtLeast(number, "build number", 0L), job);
    }

    public long number() { return number; }

    public boolean isProduction() { return job == null; }

    /** Returns the job for this, if a development revision, or throws if this is a production revision. */
    public JobId job() { return Objects.requireNonNull(job, "production revisions have no associated job"); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RevisionId that = (RevisionId) o;
        return number == that.number && Objects.equals(job, that.job);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, job);
    }

    /** Unknown, manual builds sort first, then known manual builds, then production builds, by increasing build number */
    @Override
    public int compareTo(RevisionId o) {
        return isProduction() != o.isProduction() ? Boolean.compare(isProduction(), o.isProduction())
                                                  : Long.compare(number, o.number);
    }

    @Override
    public String toString() {
        return isProduction() ? "build " + number
                              : "dev build " + number + " for " + job.type().jobName() + " of " + job.application().instance();
    }

}
