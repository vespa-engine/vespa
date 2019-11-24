// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
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
    @JsonProperty("openStackId")
    private String openStackId;
    @JsonProperty("flavor")
    private String flavor;
    @JsonProperty("canonicalFlavor")
    private String canonicalFlavor;
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
    @JsonProperty("description")
    private String description;
    @JsonProperty("history")
    private NodeHistory[] history;
    @JsonProperty("allowedToBeDown")
    private Boolean allowedToBeDown;
    @JsonProperty("reports")
    private Map<String, JsonNode> reports;
    @JsonProperty("modelName")
    private String modelName;

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

    public String getCanonicalFlavor() {
        return canonicalFlavor;
    }

    public void setCanonicalFlavor(String canonicalFlavor) {
        this.canonicalFlavor = canonicalFlavor;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public NodeHistory[] getHistory() {
        return history;
    }

    public void setHistory(NodeHistory[] history) {
        this.history = history;
    }

    public Boolean getAllowedToBeDown() {
        return allowedToBeDown;
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

    public Map<String, JsonNode> getReports() {
        return reports;
    }

    public void setReports(Map<String, JsonNode> reports) {
        this.reports = reports;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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
               ", openStackId='" + openStackId + '\'' +
               ", flavor='" + flavor + '\'' +
               ", canonicalFlavor='" + canonicalFlavor + '\'' +
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
               ", description='" + description + '\'' +
               ", history=" + Arrays.toString(history) +
               ", allowedToBeDown=" + allowedToBeDown +
               ", reports=" + reports +
               ", modelName=" + modelName +
               '}';
    }
}
