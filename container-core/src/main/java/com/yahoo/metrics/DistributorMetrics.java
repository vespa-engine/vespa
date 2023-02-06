package com.yahoo.metrics;

import java.util.List;

/**
 * @author yngveaasheim
 */
public enum DistributorMetrics implements VespaMetrics {

    VDS_IDEALSTATE_BUCKETS_RECHECKING("vds.idealstate.buckets_rechecking", Unit.BUCKET, "The number of buckets that we are rechecking for ideal state operations"),
    VDS_IDEALSTATE_IDEALSTATE_DIFF("vds.idealstate.idealstate_diff", Unit.BUCKET, "A number representing the current difference from the ideal state. This is a number that decreases steadily as the system is getting closer to the ideal state");


    private final String name;
    private final Unit unit;
    private final String description;

    DistributorMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
