// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.loadbalancer;

import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;

import java.util.Objects;

/**
 * Represents a pair of RecordId and RecordName
 *
 * @author mortent
 */
public class LoadBalancerName {
    private final RecordId recordId;
    private final RecordName recordName;

    public LoadBalancerName(RecordId recordId, RecordName recordName) {
        this.recordId = recordId;
        this.recordName = recordName;
    }

    public RecordId recordId() {
        return recordId;
    }

    public RecordName recordName() {
        return recordName;
    }

    @Override
    public String toString() {
        return "LoadBalancerName{" +
               "recordId=" + recordId +
               ", recordName=" + recordName +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoadBalancerName that = (LoadBalancerName) o;
        return recordId.equals(that.recordId) &&
               recordName.equals(that.recordName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, recordName);
    }
}
