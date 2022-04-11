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
    private final boolean production;

    private RevisionId(long number, boolean production) {
        this.number = number;
        this.production = production;
    }

    public static RevisionId forProduction(long number) {
        return new RevisionId(requireAtLeast(number, "build number", 1L), true);
    }

    public static RevisionId forDevelopment(long number) {
        return new RevisionId(requireAtLeast(number, "build number", 0L), false);
    }

    public long number() { return number; }

    public boolean isProduction() { return production; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RevisionId that = (RevisionId) o;
        return number == that.number && production == that.production;
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, production);
    }

    /** Unknown, manual builds sort first, then known manual builds, then production builds, by increasing build number */
    @Override
    public int compareTo(RevisionId o) {
        return production != o.production ? Boolean.compare(production, o.production)
                                          : Long.compare(number, o.number);
    }

    @Override
    public String toString() {
        return (production ? "prod" : "dev") + " build " + number;
    }

}
