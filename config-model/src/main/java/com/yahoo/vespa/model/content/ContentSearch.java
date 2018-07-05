// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

/**
 * @author Simon Thoresen Hult
 */
public class ContentSearch {

    private final Double queryTimeout;
    private final Double visibilityDelay;

    private ContentSearch(Builder builder) {
        queryTimeout = builder.queryTimeout;
        visibilityDelay = builder.visibilityDelay;
    }

    public Double getQueryTimeout() {
        return queryTimeout;
    }

    public Double getVisibilityDelay() {
        return visibilityDelay;
    }

    public static class Builder {

        private Double queryTimeout;
        private Double visibilityDelay;

        public ContentSearch build() {
            return new ContentSearch(this);
        }

        public Builder setQueryTimeout(Double queryTimeout) {
            this.queryTimeout = queryTimeout;
            return this;
        }

        public Builder setVisibilityDelay(Double visibilityDelay) {
            this.visibilityDelay = visibilityDelay;
            return this;
        }
    }
}
