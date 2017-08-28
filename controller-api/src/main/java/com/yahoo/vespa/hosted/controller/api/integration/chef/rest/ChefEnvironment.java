// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChefEnvironment {

    @JsonProperty("name")
    private String name;

    @JsonProperty("default_attributes")
    private Map<String, Object> attributes;
    @JsonProperty("override_attributes")
    private Map<String, Object> overrideAttributes;
    @JsonProperty("description")
    private String description;
    @JsonProperty("cookbook_versions")
    private Map<String, String> cookbookVersions;

    // internal
    @JsonProperty("json_class")
    private final String _jsonClass = "Chef::Environment";
    @JsonProperty("chef_type")
    private final String _chefType = "environment";

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public Builder copy() {
        return builder()
                .name(name)
                .attributes(attributes)
                .overrideAttributes(overrideAttributes)
                .cookbookVersions(cookbookVersions)
                .description(description);
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getCookbookVersions() {
        return cookbookVersions;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Map<String, Object> getOverrideAttributes() {
        return overrideAttributes;
    }

    public static class Builder {
        private String name;
        private Map<String, Object> attributes;
        private String description;
        private Map<String, Object> overrideAttributes;
        private Map<String, String> cookbookVersions;

        public Builder name(String name){
            this.name = name;
            return this;
        }

        public Builder attributes(Map<String, Object> defaultAttributes) {
            this.attributes = defaultAttributes;
            return this;
        }

        public Builder overrideAttributes(Map<String, Object> overrideAttributes) {
            this.overrideAttributes = overrideAttributes;
            return this;
        }

        public Builder cookbookVersions(Map<String, String> cookbookVersions) {
            this.cookbookVersions = cookbookVersions;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ChefEnvironment build() {
            ChefEnvironment chefEnvironment = new ChefEnvironment();
            chefEnvironment.name = name;
            chefEnvironment.description = description;
            chefEnvironment.cookbookVersions = cookbookVersions;
            chefEnvironment.attributes = attributes;
            chefEnvironment.overrideAttributes = overrideAttributes;

            return chefEnvironment;
        }
    }

}
