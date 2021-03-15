// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * @author olaa
 */
public class ChangeRequestSource {

    private final String system;
    private final String id;
    private final Status status;
    private final String url;
    private final ZonedDateTime plannedStartTime;
    private final ZonedDateTime plannedEndTime;


    private ChangeRequestSource(String system, String id, String url, Status status, ZonedDateTime plannedStartTime, ZonedDateTime plannedEndTime) {
        this.system = Objects.requireNonNull(system);
        this.id = Objects.requireNonNull(id);
        this.url = Objects.requireNonNull(url);
        this.status = Objects.requireNonNull(status);
        this.plannedStartTime = Objects.requireNonNull(plannedStartTime);
        this.plannedEndTime = Objects.requireNonNull(plannedEndTime);
    }

    public String getSystem() {
        return system;
    }

    public String getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public String getUrl() {
        return url;
    }

    public ZonedDateTime getPlannedStartTime() {
        return plannedStartTime;
    }

    public ZonedDateTime getPlannedEndTime() {
        return plannedEndTime;
    }

    @Override
    public String toString() {
        return "ChangeRequestSource{" +
                "system='" + system + '\'' +
                ", id='" + id + '\'' +
                ", status=" + status +
                ", url='" + url + '\'' +
                ", plannedStartTime=" + plannedStartTime +
                ", plannedEndTime=" + plannedEndTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangeRequestSource that = (ChangeRequestSource) o;
        return Objects.equals(system, that.system) &&
                Objects.equals(id, that.id) &&
                status == that.status &&
                Objects.equals(url, that.url) &&
                Objects.equals(plannedStartTime, that.plannedStartTime) &&
                Objects.equals(plannedEndTime, that.plannedEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(system, id, status, url, plannedStartTime, plannedEndTime);
    }

    public enum Status {
        DRAFT,
        WAITING_FOR_APPROVAL,
        APPROVED,
        STARTED,
        COMPLETE,
        CLOSED,
        CANCELED,
        UNKNOWN_STATUS
    }

    public static class Builder {
        private String system;
        private String id;
        private String url;
        private Status status;
        private ZonedDateTime plannedStartTime;
        private ZonedDateTime plannedEndTime;

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder plannedStartTime(ZonedDateTime plannedStartTime) {
            this.plannedStartTime = plannedStartTime;
            return this;
        }

        public Builder plannedEndTime(ZonedDateTime plannedEndTime) {
            this.plannedEndTime = plannedEndTime;
            return this;
        }

        public ChangeRequestSource build() {
            return new ChangeRequestSource(system, id, url, status, plannedStartTime, plannedEndTime);
        }
    }

}
