package com.yahoo.vespa.model.content;

public record CoveragePolicy(Policy policy) {

    public enum Policy { GROUP, NODE }

    public static CoveragePolicy from(String policy) {
        if (policy == null || policy.equals("group")) {
            return new CoveragePolicy(Policy.GROUP);
        } else if (policy.equals("node")) {
            return new CoveragePolicy(Policy.NODE);
        } else {
            throw new IllegalArgumentException("Unknown policy: " + policy);
        }
    }

    static CoveragePolicy group() { return new CoveragePolicy(Policy.GROUP); }

    static CoveragePolicy node() { return new CoveragePolicy(Policy.NODE); }

}
