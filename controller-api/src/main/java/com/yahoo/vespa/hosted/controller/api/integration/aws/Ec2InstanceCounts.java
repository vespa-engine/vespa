// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import java.util.Map;
import java.util.Objects;

/**
 * @author freva
 */
public class Ec2InstanceCounts {
    private final int totalCount;
    private final Map<String, Integer> instanceCounts;

    public Ec2InstanceCounts(int totalCount, Map<String, Integer> instanceCounts) {
        this.totalCount = totalCount;
        this.instanceCounts = Map.copyOf(instanceCounts);
    }

    public int getTotalCount() {
        return totalCount;
    }

    /** Returns map of counts by instance type, e.g. 'r5.2xlarge' */
    public Map<String, Integer> getInstanceCounts() {
        return instanceCounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ec2InstanceCounts that = (Ec2InstanceCounts) o;
        return totalCount == that.totalCount &&
                instanceCounts.equals(that.instanceCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalCount, instanceCounts);
    }

    @Override
    public String toString() {
        return "Ec2InstanceLimits{" +
                "totalLimit=" + totalCount +
                ", instanceCounts=" + instanceCounts +
                '}';
    }
}
