// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import java.util.Map;

/**
 * @author freva
 */
public class Ec2InstanceCounts {
    private final int totalCount;
    private final Map<String, Integer> instanceCounts;

    private Ec2InstanceCounts(int totalCount, Map<String, Integer> instanceCounts) {
        this.totalCount = totalCount;
        this.instanceCounts = Map.copyOf(instanceCounts);
    }

    public int getTotalCount() {
        return totalCount;
    }

    public Map<String, Integer> getInstanceCounts() {
        return instanceCounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ec2InstanceCounts that = (Ec2InstanceCounts) o;

        if (totalCount != that.totalCount) return false;
        return instanceCounts.equals(that.instanceCounts);
    }

    @Override
    public int hashCode() {
        int result = totalCount;
        result = 31 * result + instanceCounts.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Ec2InstanceLimits{" +
                "totalLimit=" + totalCount +
                ", instanceCounts=" + instanceCounts +
                '}';
    }
}
