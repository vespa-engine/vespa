// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import static com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource.Status.*;

/**
 * @author olaa
 */
public record ChangeRequestSource(String system,
                                  String id,
                                  String url,
                                  Status status,
                                  ZonedDateTime plannedStartTime,
                                  ZonedDateTime plannedEndTime,
                                  String category) {

    public boolean isClosed() {
        return List.of(CLOSED, CANCELED, COMPLETE).contains(status);
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
        private String category;

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

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public ChangeRequestSource build() {
            return new ChangeRequestSource(system, id, url, status, plannedStartTime, plannedEndTime, category);
        }
    }

}
