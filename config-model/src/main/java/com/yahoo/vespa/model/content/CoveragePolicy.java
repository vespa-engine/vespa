package com.yahoo.vespa.model.content;

public record CoveragePolicy(Policy policy) {

    enum Policy { GROUP, NODE }

    static CoveragePolicy group() { return new CoveragePolicy(Policy.GROUP); }

    static CoveragePolicy node() { return new CoveragePolicy(Policy.NODE); }

}
