// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.zone.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

/**
 * @author smorgrav
 */
public class ZoneReference {

    public static class Environment {
        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private URI url;

        public String getName() {
            return name;
        }

        public Environment setName(String name) {
            this.name = name;
            return this;
        }

        public URI getUrl() {
            return url;
        }

        public Environment setUrl(URI url) {
            this.url = url;
            return this;
        }
    }

    public static class Region {
        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private URI url;

        public String getName() {
            return name;
        }

        public Region setName(String name) {
            this.name = name;
            return this;
        }

        public URI getUrl() {
            return url;
        }

        public Region setUrl(URI url) {
            this.url = url;
            return this;
        }
    }
}
