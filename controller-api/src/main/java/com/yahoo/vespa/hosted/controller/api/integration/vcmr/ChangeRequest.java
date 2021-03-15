// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.util.List;
import java.util.Objects;

/**
 * @author olaa
 */
public class ChangeRequest {

    private final String id;
    private final ChangeRequestSource changeRequestSource;
    private final List<String> impactedSwitches;
    private final List<String> impactedHosts;
    private final boolean isApproved;
    private final Impact impact;

    private ChangeRequest(String id, ChangeRequestSource changeRequestSource, List<String> impactedSwitches, List<String> impactedHosts, boolean isApproved, Impact impact) {
        this.id = Objects.requireNonNull(id);
        this.changeRequestSource = Objects.requireNonNull(changeRequestSource);
        this.impactedSwitches = Objects.requireNonNull(impactedSwitches);
        this.impactedHosts = Objects.requireNonNull(impactedHosts);
        this.isApproved = Objects.requireNonNull(isApproved);
        this.impact = Objects.requireNonNull(impact);
    }

    public String getId() {
        return id;
    }

    public ChangeRequestSource getChangeRequestSource() {
        return changeRequestSource;
    }

    public List<String> getImpactedSwitches() {
        return impactedSwitches;
    }

    public List<String> getImpactedHosts() {
        return impactedHosts;
    }

    public boolean isApproved() {
        return isApproved;
    }

    public Impact getImpact() {
        return impact;
    }

    @Override
    public String toString() {
        return "ChangeRequest{" +
                "id='" + id + '\'' +
                ", changeRequestSource=" + changeRequestSource +
                ", impactedSwitches=" + impactedSwitches +
                ", impactedHosts=" + impactedHosts +
                ", isApproved=" + isApproved +
                ", impact=" + impact +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangeRequest that = (ChangeRequest) o;
        return isApproved == that.isApproved &&
                Objects.equals(id, that.id) &&
                Objects.equals(changeRequestSource, that.changeRequestSource) &&
                Objects.equals(impactedSwitches, that.impactedSwitches) &&
                Objects.equals(impactedHosts, that.impactedHosts) &&
                impact == that.impact;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, changeRequestSource, impactedSwitches, impactedHosts, isApproved, impact);
    }

    public static class Builder {
        private String id;
        private ChangeRequestSource changeRequestSource;
        private List<String> impactedSwitches;
        private List<String> impactedHosts;
        private boolean isApproved;
        private Impact impact;


        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder changeRequestSource(ChangeRequestSource changeRequestSource) {
            this.changeRequestSource = changeRequestSource;
            return this;
        }

        public Builder impactedSwitches(List<String> impactedSwitches) {
            this.impactedSwitches = impactedSwitches;
            return this;
        }

        public Builder impactedHosts(List<String> impactedHosts) {
            this.impactedHosts = impactedHosts;
            return this;
        }

        public Builder isApproved(boolean isApproved) {
            this.isApproved = isApproved;
            return this;
        }

        public Builder impact(Impact impact) {
            this.impact = impact;
            return this;
        }

        public ChangeRequest build() {
            return new ChangeRequest(id, changeRequestSource, impactedSwitches, impactedHosts, isApproved, impact);
        }

        public String getId() {
            return this.id;
        }
    }

    public enum Impact {
        NONE,
        LOW,
        MODERATE,
        HIGH,
        VERY_HIGH,
        UNKNOWN
    }

}
