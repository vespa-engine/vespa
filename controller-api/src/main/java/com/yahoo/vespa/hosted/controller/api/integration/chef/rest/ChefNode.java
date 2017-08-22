// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChefNode {

    @JsonProperty("name")
    public String name;

    @JsonProperty("chef_environment")
    public String chefEnvironment;

    @JsonProperty("run_list")
    public List<String> runList;

    @JsonProperty("json_class")
    public String jsonClass;

    @JsonProperty("chef_type")
    public String chefType;

    @JsonProperty("automatic")
    public Map<String, Object> automaticAttributes;

    @JsonProperty("normal")
    public Map<String, Object> normalAttributes;

    @JsonProperty("default")
    public Map<String, Object> defaultAttributes;

    @JsonProperty("override")
    public Map<String, Object> overrideAttributes;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ChefNode src) {
        return new Builder(src);
    }

    public static class Builder {
        private String name;
        private String chefEnvironment;
        private List<String> runList;
        private String jsonClass;
        private String chefType;
        private Map<String, Object> automaticAttributes;
        private Map<String, Object> normalAttributes;
        private Map<String, Object> defaultAttributes;
        private Map<String, Object> overrideAttributes;

        private Builder(){}

        private Builder(ChefNode src){
            this.name = src.name;
            this.chefEnvironment = src.chefEnvironment;
            this.runList = new ArrayList<>(src.runList);
            this.jsonClass = src.jsonClass;
            this.chefType = src.chefType;
            this.automaticAttributes = new HashMap<>(src.automaticAttributes);
            this.normalAttributes = new HashMap<>(src.normalAttributes);
            this.defaultAttributes = new HashMap<>(src.defaultAttributes);
            this.overrideAttributes = new HashMap<>(src.overrideAttributes);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder chefEnvironment(String chefEnvironment) {
            this.chefEnvironment = chefEnvironment;
            return this;
        }

        public ChefNode build(){
            ChefNode node = new ChefNode();
            node.name = this.name;
            node.chefEnvironment = this.chefEnvironment;
            node.runList = this.runList;
            node.jsonClass = this.jsonClass;
            node.chefType = this.chefType;
            node.automaticAttributes = this.automaticAttributes;
            node.overrideAttributes = this.overrideAttributes;
            node.defaultAttributes = this.defaultAttributes;
            node.normalAttributes = this.normalAttributes;
            return node;
        }

    }

    @Override
    public String toString() {
        return "Node{" +
                "name='" + name + '\'' +
                ", chefEnvironment='" + chefEnvironment + '\'' +
                ", runList=" + runList +
                ", jsonClass='" + jsonClass + '\'' +
                ", chefType='" + chefType + '\'' +
                ", automaticAttributes=" + automaticAttributes +
                ", normalAttributes=" + normalAttributes +
                ", defaultAttributes=" + defaultAttributes +
                ", overrideAttributes=" + overrideAttributes +
                '}';
    }
}
