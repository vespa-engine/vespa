// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeRepositoryNode {

    @JsonProperty("state")
    public String state;
    @JsonProperty("hostname")
    public String hostname;
    @JsonProperty("ipAddresses")
    public Set<String> ipAddresses;
    @JsonProperty("additionalIpAddresses")
    public Set<String> additionalIpAddresses;
    @JsonProperty("openStackId")
    public String openStackId;
    @JsonProperty("flavor")
    public String flavor;
    @JsonProperty("resources")
    public NodeResources resources;
    @JsonProperty("membership")
    public Membership membership;
    @JsonProperty("owner")
    public Owner owner;
    @JsonProperty("restartGeneration")
    public Long restartGeneration;
    @JsonProperty("rebootGeneration")
    public Long rebootGeneration;
    @JsonProperty("currentRestartGeneration")
    public Long currentRestartGeneration;
    @JsonProperty("currentRebootGeneration")
    public Long currentRebootGeneration;
    @JsonProperty("vespaVersion")
    public String vespaVersion;
    @JsonProperty("wantedVespaVersion")
    public String wantedVespaVersion;
    @JsonProperty("currentOsVersion")
    public String currentOsVersion;
    @JsonProperty("wantedOsVersion")
    public String wantedOsVersion;
    @JsonProperty("currentFirmwareCheck")
    public Long currentFirmwareCheck;
    @JsonProperty("wantedFirmwareCheck")
    public Long wantedFirmwareCheck;
    @JsonProperty("modelName")
    public String modelName;
    @JsonProperty("failCount")
    public Integer failCount;
    @JsonProperty("environment")
    public String environment;
    @JsonProperty("reservedTo")
    public String reservedTo;
    @JsonProperty("type")
    public String type;
    @JsonProperty("wantedDockerImage")
    public String wantedDockerImage;
    @JsonProperty("currentDockerImage")
    public String currentDockerImage;
    @JsonProperty("parentHostname")
    public String parentHostname;
    @JsonProperty("wantToRetire")
    public Boolean wantToRetire;
    @JsonProperty("wantToDeprovision")
    public Boolean wantToDeprovision;
    @JsonProperty("orchestratorStatus")
    public String orchestratorStatus;
    @JsonProperty("archiveUri")
    public String archiveUri;
    @JsonProperty("exclusiveTo")
    public String exclusiveTo;

    @JsonProperty("reports")
    public Map<String, JsonNode> reports = null;

    @Override
    public String toString() {
        return "NodeRepositoryNode{" +
                "state='" + state + '\'' +
                ", hostname='" + hostname + '\'' +
                ", ipAddresses=" + ipAddresses +
                ", additionalIpAddresses=" + additionalIpAddresses +
                ", openStackId='" + openStackId + '\'' +
                ", modelName='" + modelName + '\'' +
                ", flavor='" + flavor + '\'' +
                ", resources=" + resources +
                ", membership=" + membership +
                ", owner=" + owner +
                ", restartGeneration=" + restartGeneration +
                ", rebootGeneration=" + rebootGeneration +
                ", currentRestartGeneration=" + currentRestartGeneration +
                ", currentRebootGeneration=" + currentRebootGeneration +
                ", vespaVersion='" + vespaVersion + '\'' +
                ", wantedVespaVersion='" + wantedVespaVersion + '\'' +
                ", currentOsVersion='" + currentOsVersion + '\'' +
                ", wantedOsVersion='" + wantedOsVersion + '\'' +
                ", currentFirmwareCheck=" + currentFirmwareCheck +
                ", wantedFirmwareCheck=" + wantedFirmwareCheck +
                ", failCount=" + failCount +
                ", environment='" + environment + '\'' +
                ", reservedTo='" + reservedTo + "'" +
                ", type='" + type + '\'' +
                ", wantedDockerImage='" + wantedDockerImage + '\'' +
                ", currentDockerImage='" + currentDockerImage + '\'' +
                ", parentHostname='" + parentHostname + '\'' +
                ", wantToRetire=" + wantToRetire +
                ", wantToDeprovision=" + wantToDeprovision +
                ", orchestratorStatus=" + orchestratorStatus +
                ", archiveUri=" + archiveUri +
                ", reports=" + reports +
                ", exclusiveTo=" + exclusiveTo +
                '}';
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {
        @JsonProperty("tenant")
        public String tenant;
        @JsonProperty("application")
        public String application;
        @JsonProperty("instance")
        public String instance;

        public String toString() {
            return "Owner {" +
                    " tenant = " + tenant +
                    " application = " + application +
                    " instance = " + instance +
                    " }";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Membership {
        @JsonProperty("clustertype")
        public String clusterType;
        @JsonProperty("clusterid")
        public String clusterId;
        @JsonProperty("group")
        public String group;
        @JsonProperty("index")
        public int index;
        @JsonProperty("retired")
        public boolean retired;

        @Override
        public String toString() {
            return "Membership {" +
                    " clusterType = " + clusterType +
                    " clusterId = " + clusterId +
                    " group = " + group +
                    " index = " + index +
                    " retired = " + retired +
                    " }";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NodeResources {
        @JsonProperty
        public Double vcpu;
        @JsonProperty
        public Double memoryGb;
        @JsonProperty
        public Double diskGb;
        @JsonProperty
        public Double bandwidthGbps;
        @JsonProperty
        public String diskSpeed;
        @JsonProperty
        public String storageType;

        @Override
        public String toString() {
            return "NodeResources{" +
                    "vcpu=" + vcpu +
                    ", memoryGb=" + memoryGb +
                    ", diskGb=" + diskGb +
                    ", bandwidthGbps=" + bandwidthGbps +
                    ", diskSpeed='" + diskSpeed + '\'' +
                    ", storageType='" + storageType + '\'' +
                    '}';
        }
    }

}
