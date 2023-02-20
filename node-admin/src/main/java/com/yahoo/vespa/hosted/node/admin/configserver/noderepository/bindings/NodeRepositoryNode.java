// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
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
    @JsonProperty("id")
    public String id;
    @JsonProperty("flavor")
    public String flavor;
    @JsonProperty("resources")
    public NodeResources resources;
    @JsonProperty("realResources")
    public NodeResources realResources;
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
    @JsonProperty("wantToRebuild")
    public Boolean wantToRebuild;
    @JsonProperty("orchestratorStatus")
    public String orchestratorStatus;
    @JsonProperty("archiveUri")
    public String archiveUri;
    @JsonProperty("exclusiveTo")
    public String exclusiveTo;
    @JsonProperty("history")
    public List<Event> history;
    @JsonProperty("trustStore")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<TrustStoreItem> trustStore;
    @JsonProperty("wireguardPubkey")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String wireguardPubkey;

    @JsonProperty("reports")
    public Map<String, JsonNode> reports = null;

    @Override
    public String toString() {
        return "NodeRepositoryNode{" +
               "state='" + state + '\'' +
               ", hostname='" + hostname + '\'' +
               ", ipAddresses=" + ipAddresses +
               ", additionalIpAddresses=" + additionalIpAddresses +
               ", id='" + id + '\'' +
               ", flavor='" + flavor + '\'' +
               ", resources=" + resources +
               ", realResources=" + realResources +
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
               ", modelName='" + modelName + '\'' +
               ", failCount=" + failCount +
               ", environment='" + environment + '\'' +
               ", reservedTo='" + reservedTo + '\'' +
               ", type='" + type + '\'' +
               ", wantedDockerImage='" + wantedDockerImage + '\'' +
               ", currentDockerImage='" + currentDockerImage + '\'' +
               ", parentHostname='" + parentHostname + '\'' +
               ", wantToRetire=" + wantToRetire +
               ", wantToDeprovision=" + wantToDeprovision +
               ", wantToRebuild=" + wantToRebuild +
               ", orchestratorStatus='" + orchestratorStatus + '\'' +
               ", archiveUri='" + archiveUri + '\'' +
               ", exclusiveTo='" + exclusiveTo + '\'' +
               ", history=" + history +
               ", trustStore=" + trustStore +
               ", wireguardPubkey=" + wireguardPubkey +
               ", reports=" + reports +
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
        @JsonProperty
        public String architecture;
        @JsonProperty
        public Integer gpuCount;
        @JsonProperty
        public Double gpuMemoryGb;

        @Override
        public String toString() {
            return "NodeResources{" +
                   "vcpu=" + vcpu +
                   ", memoryGb=" + memoryGb +
                   ", diskGb=" + diskGb +
                   ", bandwidthGbps=" + bandwidthGbps +
                   ", diskSpeed='" + diskSpeed + '\'' +
                   ", storageType='" + storageType + '\'' +
                   ", architecture='" + architecture + '\'' +
                   ", gpuCount=" + gpuCount +
                   ", gpuMemoryGb=" + gpuMemoryGb +
                   '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Event {
        @JsonProperty
        public String event;
        @JsonProperty
        public String agent;
        @JsonProperty
        public Long at;

        @Override
        public String toString() {
            return "Event{" +
                    "agent=" + agent +
                    ", event=" + event +
                    ", at=" + at +
                    '}';
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrustStoreItem {
        @JsonProperty ("fingerprint")
        public String fingerprint;
        @JsonProperty ("expiry")
        public long expiry;

        public TrustStoreItem(@JsonProperty("fingerprint") String fingerprint, @JsonProperty("expiry") long expiry) {
            this.fingerprint = fingerprint;
            this.expiry = expiry;
        }
    }
}
