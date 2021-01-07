// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeRepositoryNode {

    @JsonProperty("url")
    private String url;
    @JsonProperty("id")
    private String id;
    @JsonProperty("state")
    private NodeState state;
    @JsonProperty("hostname")
    private String hostname;
    @JsonProperty("ipAddresses")
    private Set<String> ipAddresses;
    @JsonProperty("additionalIpAddresses")
    private Set<String> additionalIpAddresses;
    @JsonProperty("additionalHostnames")
    private List<String> additionalHostnames;
    @JsonProperty("openStackId")
    private String openStackId;
    @JsonProperty("flavor")
    private String flavor;
    @JsonProperty("resources")
    private NodeResources resources;
    @JsonProperty("requestedResources")
    private NodeResources requestedResources;
    @JsonProperty("membership")
    private NodeMembership membership;
    @JsonProperty("owner")
    private NodeOwner owner;
    @JsonProperty("restartGeneration")
    private Integer restartGeneration;
    @JsonProperty("rebootGeneration")
    private Integer rebootGeneration;
    @JsonProperty("currentRestartGeneration")
    private Integer currentRestartGeneration;
    @JsonProperty("currentRebootGeneration")
    private Integer currentRebootGeneration;
    @JsonProperty("vespaVersion")
    private String vespaVersion;
    @JsonProperty("wantedVespaVersion")
    private String wantedVespaVersion;
    @JsonProperty("currentOsVersion")
    private String currentOsVersion;
    @JsonProperty("wantedOsVersion")
    private String wantedOsVersion;
    @JsonProperty("currentFirmwareCheck")
    private Long currentFirmwareCheck;
    @JsonProperty("wantedFirmwareCheck")
    private Long wantedFirmwareCheck;
    @JsonProperty("failCount")
    private Integer failCount;
    @JsonProperty("environment")
    private NodeEnvironment environment;
    @JsonProperty("type")
    private NodeType type;
    @JsonProperty("wantedDockerImage")
    private String wantedDockerImage;
    @JsonProperty("currentDockerImage")
    private String currentDockerImage;
    @JsonProperty("parentHostname")
    private String parentHostname;
    @JsonProperty("wantToRetire")
    private Boolean wantToRetire;
    @JsonProperty("wantToDeprovision")
    private Boolean wantToDeprovision;
    @JsonProperty("cost")
    private Integer cost;
    @JsonProperty("history")
    private List<NodeHistory> history;
    @JsonProperty("orchestratorStatus")
    private String orchestratorStatus;
    @JsonProperty("suspendedSinceMillis")
    private Long suspendedSinceMillis;
    @JsonProperty("reports")
    private Map<String, JsonNode> reports;
    @JsonProperty("modelName")
    private String modelName;
    @JsonProperty("reservedTo")
    private String reservedTo;
    @JsonProperty("exclusiveTo")
    private String exclusiveTo;
    @JsonProperty("switchHostname")
    private String switchHostname;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeState getState() {
        return state;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Set<String> getIpAddresses() {
        return ipAddresses;
    }

    public Set<String> getAdditionalIpAddresses() {
        return additionalIpAddresses;
    }

    public void setIpAddresses(Set<String> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    public void setAdditionalIpAddresses(Set<String> additionalIpAddresses) {
        this.additionalIpAddresses = additionalIpAddresses;
    }

    public List<String> getAdditionalHostnames() {
        return additionalHostnames;
    }

    public void setAdditionalHostnames(List<String> additionalHostnames) {
        this.additionalHostnames = additionalHostnames;
    }

    public String getOpenStackId() {
        return openStackId;
    }

    public void setOpenStackId(String openStackId) {
        this.openStackId = openStackId;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public NodeResources getResources() {
        return resources;
    }

    public void setResources(NodeResources resources) {
        this.resources = resources;
    }

    public NodeResources getRequestedResources() {
        return requestedResources;
    }

    public void setRequestedResources(NodeResources requestedResources) {
        this.requestedResources = requestedResources;
    }

    public NodeMembership getMembership() {
        return membership;
    }

    public void setMembership(NodeMembership membership) {
        this.membership = membership;
    }

    public NodeOwner getOwner() {
        return owner;
    }

    public void setOwner(NodeOwner owner) {
        this.owner = owner;
    }

    public Integer getRestartGeneration() {
        return restartGeneration;
    }

    public void setRestartGeneration(Integer restartGeneration) {
        this.restartGeneration = restartGeneration;
    }

    public Integer getRebootGeneration() {
        return rebootGeneration;
    }

    public void setRebootGeneration(Integer rebootGeneration) {
        this.rebootGeneration = rebootGeneration;
    }

    public Integer getCurrentRestartGeneration() {
        return currentRestartGeneration;
    }

    public void setCurrentRestartGeneration(Integer currentRestartGeneration) {
        this.currentRestartGeneration = currentRestartGeneration;
    }

    public Integer getCurrentRebootGeneration() {
        return currentRebootGeneration;
    }

    public void setCurrentRebootGeneration(Integer currentRebootGeneration) {
        this.currentRebootGeneration = currentRebootGeneration;
    }

    public String getVespaVersion() {
        return vespaVersion;
    }

    public void setVespaVersion(String vespaVersion) {
        this.vespaVersion = vespaVersion;
    }

    public String getWantedVespaVersion() {
        return wantedVespaVersion;
    }

    public void setWantedVespaVersion(String wantedVespaVersion) {
        this.wantedVespaVersion = wantedVespaVersion;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public NodeEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(NodeEnvironment environment) {
        this.environment = environment;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getWantedDockerImage() {
        return wantedDockerImage;
    }

    public void setWantedDockerImage(String wantedDockerImage) {
        this.wantedDockerImage = wantedDockerImage;
    }

    public String getCurrentDockerImage() {
        return currentDockerImage;
    }

    public void setCurrentDockerImage(String currentDockerImage) {
        this.currentDockerImage = currentDockerImage;
    }

    public String getParentHostname() {
        return parentHostname;
    }

    public void setParentHostname(String parentHostname) {
        this.parentHostname = parentHostname;
    }

    public Boolean getWantToRetire() {
        return wantToRetire;
    }

    public Boolean getWantToDeprovision() { return wantToDeprovision; }

    public void setWantToRetire(Boolean wantToRetire) {
        this.wantToRetire = wantToRetire;
    }

    public void setWantToDeprovision(Boolean wantToDeprovision) {
        this.wantToDeprovision = wantToDeprovision;
    }

    public Integer getCost() {
        return cost;
    }

    public void setCost(Integer cost) {
        this.cost = cost;
    }

    public List<NodeHistory> getHistory() {
        return history;
    }

    public void setHistory(List<NodeHistory> history) {
        this.history = history;
    }


    @JsonGetter("orchestratorStatus")
    public String getOrchestratorStatusOrNull() {
        return orchestratorStatus;
    }

    @JsonIgnore
    public OrchestratorStatus getOrchestratorStatus() {
        if (orchestratorStatus == null) {
            return OrchestratorStatus.NO_REMARKS;
        }

        return OrchestratorStatus.fromString(orchestratorStatus);
    }

    public Long suspendedSinceMillis() {
        return suspendedSinceMillis;
    }

    public void setSuspendedSinceMillis(long suspendedSinceMillis) {
        this.suspendedSinceMillis = suspendedSinceMillis;
    }

    public String getCurrentOsVersion() {
        return currentOsVersion;
    }

    public void setCurrentOsVersion(String currentOsVersion) {
        this.currentOsVersion = currentOsVersion;
    }

    public String getWantedOsVersion() {
        return wantedOsVersion;
    }

    public void setWantedOsVersion(String wantedOsVersion) {
        this.wantedOsVersion = wantedOsVersion;
    }

    public Long getCurrentFirmwareCheck() {
        return currentFirmwareCheck;
    }

    public void setCurrentFirmwareCheck(Long currentFirmwareCheck) {
        this.currentFirmwareCheck = currentFirmwareCheck;
    }

    public Long getWantedFirmwareCheck() {
        return wantedFirmwareCheck;
    }

    public void setWantedFirmwareCheck(Long wantedFirmwareCheck) {
        this.wantedFirmwareCheck = wantedFirmwareCheck;
    }

    @JsonIgnore
    public Map<String, JsonNode> getReports() {
        return reports == null ? Map.of() : reports;
    }

    @JsonGetter("reports")
    public Map<String, JsonNode> getReportsOrNull() { return reports; }

    public void setReports(Map<String, JsonNode> reports) {
        this.reports = reports;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getReservedTo() { return reservedTo; }

    public void setReservedTo(String reservedTo) { this.reservedTo = reservedTo; }

    public String getExclusiveTo() { return exclusiveTo; }

    public void setExclusiveTo(String exclusiveTo) { this.exclusiveTo = exclusiveTo; }

    public String getSwitchHostname() {
        return switchHostname;
    }

    public void setSwitchHostname(String switchHostname) {
        this.switchHostname = switchHostname;
    }

    @Override
    public String toString() {
        return "NodeRepositoryNode{" +
               "url='" + url + '\'' +
               ", id='" + id + '\'' +
               ", state=" + state +
               ", hostname='" + hostname + '\'' +
               ", ipAddresses=" + ipAddresses +
               ", additionalIpAddresses=" + additionalIpAddresses +
               ", additionalHostnames=" + additionalHostnames +
               ", openStackId='" + openStackId + '\'' +
               ", flavor='" + flavor + '\'' +
               ", resources=" + resources +
               ", requestedResources=" + requestedResources +
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
               ", failCount=" + failCount +
               ", environment=" + environment +
               ", type=" + type +
               ", wantedDockerImage='" + wantedDockerImage + '\'' +
               ", currentDockerImage='" + currentDockerImage + '\'' +
               ", parentHostname='" + parentHostname + '\'' +
               ", wantToRetire=" + wantToRetire +
               ", wantToDeprovision=" + wantToDeprovision +
               ", cost=" + cost +
               ", history=" + history +
               ", orchestratorStatus=" + orchestratorStatus +
               ", reports=" + reports +
               ", modelName=" + modelName +
               ", reservedTo=" + reservedTo +
               ", exclusiveTo=" + exclusiveTo +
               ", switchHostname=" + switchHostname +
               '}';
    }

}
