// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

/**
 * The wire format of a node retrieved from the node repository.
 *
 * All fields in this are nullable.
 *
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeRepositoryNode {

    @JsonProperty("url")
    private String url;
    @JsonProperty("id")
    private String id;
    @JsonProperty("state")
    private String state;
    @JsonProperty("hostname")
    private String hostname;
    @JsonProperty("ipAddresses")
    private Set<String> ipAddresses;
    @JsonProperty("additionalIpAddresses")
    private Set<String> additionalIpAddresses;
    @JsonProperty("additionalHostnames")
    private List<String> additionalHostnames;
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
    @JsonProperty("deferOsUpgrade")
    private Boolean deferOsUpgrade;
    @JsonProperty("currentFirmwareCheck")
    private Long currentFirmwareCheck;
    @JsonProperty("wantedFirmwareCheck")
    private Long wantedFirmwareCheck;
    @JsonProperty("failCount")
    private Integer failCount;
    @JsonProperty("environment")
    private String environment;
    @JsonProperty("type")
    private String type;
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
    @JsonProperty("wantToRebuild")
    private Boolean wantToRebuild;
    @JsonProperty("down")
    private Boolean down;
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
    @JsonProperty("cloudAccount")
    private String cloudAccount;

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

    public String getState() {
        return state;
    }

    public void setState(String state) {
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

    public Boolean getDeferOsUpgrade() {
        return deferOsUpgrade;
    }

    public void setDeferOsUpgrade(Boolean deferOsUpgrade) {
        this.deferOsUpgrade = deferOsUpgrade;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
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

    public Boolean getWantToRebuild() {
        return wantToRebuild;
    }

    public void setWantToRetire(Boolean wantToRetire) {
        this.wantToRetire = wantToRetire;
    }

    public void setWantToDeprovision(Boolean wantToDeprovision) {
        this.wantToDeprovision = wantToDeprovision;
    }

    public void setWantToRebuild(Boolean wantToRebuild) {
        this.wantToRebuild = wantToRebuild;
    }

    public Boolean getDown() {
        return down;
    }

    public void setDown(Boolean down) {
        this.down = down;
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

    public String getOrchestratorStatus() {
        return orchestratorStatus;
    }

    public void setOrchestratorStatus(String orchestratorStatus) {
        this.orchestratorStatus = orchestratorStatus;
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

    public String getCloudAccount() {
        return cloudAccount;
    }

    public void setCloudAccount(String cloudAccount) {
        this.cloudAccount = cloudAccount;
    }

    // --- Helper methods for code that (wrongly) consume this directly

    public boolean hasType(NodeType type) {
        return type.name().equals(getType());
    }

    public boolean hasState(NodeState state) {
        return state.name().equals(getState());
    }

    // --- end


    @Override
    public String toString() {
        return "NodeRepositoryNode{" +
               "url='" + url + '\'' +
               ", id='" + id + '\'' +
               ", state='" + state + '\'' +
               ", hostname='" + hostname + '\'' +
               ", ipAddresses=" + ipAddresses +
               ", additionalIpAddresses=" + additionalIpAddresses +
               ", additionalHostnames=" + additionalHostnames +
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
               ", deferOsUpgrade=" + deferOsUpgrade +
               ", currentFirmwareCheck=" + currentFirmwareCheck +
               ", wantedFirmwareCheck=" + wantedFirmwareCheck +
               ", failCount=" + failCount +
               ", environment='" + environment + '\'' +
               ", type='" + type + '\'' +
               ", wantedDockerImage='" + wantedDockerImage + '\'' +
               ", currentDockerImage='" + currentDockerImage + '\'' +
               ", parentHostname='" + parentHostname + '\'' +
               ", wantToRetire=" + wantToRetire +
               ", wantToDeprovision=" + wantToDeprovision +
               ", wantToRebuild=" + wantToRebuild +
               ", down=" + down +
               ", cost=" + cost +
               ", history=" + history +
               ", orchestratorStatus='" + orchestratorStatus + '\'' +
               ", suspendedSinceMillis=" + suspendedSinceMillis +
               ", reports=" + reports +
               ", modelName='" + modelName + '\'' +
               ", reservedTo='" + reservedTo + '\'' +
               ", exclusiveTo='" + exclusiveTo + '\'' +
               ", switchHostname='" + switchHostname + '\'' +
               ", cloudAccount='" + cloudAccount + '\'' +
               '}';
    }

}
