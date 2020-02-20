// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A node in the node repository is modified by setting which attributes to modify in this class,
 * and then patching the node repository node through {@link NodeRepository#updateNodeAttributes(String, NodeAttributes)}.
 *
 * @author Haakon Dybdahl
 * @author Valerij Fredriksen
 */
public class NodeAttributes {

    private Optional<Long> restartGeneration = Optional.empty();
    private Optional<Long> rebootGeneration = Optional.empty();
    private Optional<DockerImage> dockerImage = Optional.empty();
    private Optional<Version> vespaVersion = Optional.empty();
    private Optional<Version> currentOsVersion = Optional.empty();
    private Optional<Instant> currentFirmwareCheck = Optional.empty();
    private Optional<Boolean> wantToDeprovision = Optional.empty();
    private Optional<TenantName> reservedTo = Optional.empty();
    /** The list of reports to patch. A null value is used to remove the report. */
    private Map<String, JsonNode> reports = new TreeMap<>();

    public NodeAttributes() { }

    public NodeAttributes withRestartGeneration(Optional<Long> restartGeneration) {
        this.restartGeneration = restartGeneration;
        return this;
    }

    public NodeAttributes withRestartGeneration(long restartGeneration) {
        return withRestartGeneration(Optional.of(restartGeneration));
    }

    public NodeAttributes withRebootGeneration(long rebootGeneration) {
        this.rebootGeneration = Optional.of(rebootGeneration);
        return this;
    }

    public NodeAttributes withDockerImage(DockerImage dockerImage) {
        this.dockerImage = Optional.of(dockerImage);
        return this;
    }

    public NodeAttributes withVespaVersion(Version vespaVersion) {
        this.vespaVersion = Optional.of(vespaVersion);
        return this;
    }

    public NodeAttributes withCurrentOsVersion(Version currentOsVersion) {
        this.currentOsVersion = Optional.of(currentOsVersion);
        return this;
    }

    public NodeAttributes withCurrentFirmwareCheck(Instant currentFirmwareCheck) {
        this.currentFirmwareCheck = Optional.of(currentFirmwareCheck);
        return this;
    }


    public NodeAttributes withWantToDeprovision(boolean wantToDeprovision) {
        this.wantToDeprovision = Optional.of(wantToDeprovision);
        return this;
    }

    public NodeAttributes withReports(Map<String, JsonNode> nodeReports) {
        this.reports = new TreeMap<>(nodeReports);
        return this;
    }

    public NodeAttributes withReport(String reportId, JsonNode jsonNode) {
        reports.put(reportId, jsonNode);
        return this;
    }

    public NodeAttributes withReportRemoved(String reportId) {
        reports.put(reportId, null);
        return this;
    }

    public Optional<Long> getRestartGeneration() {
        return restartGeneration;
    }

    public Optional<Long> getRebootGeneration() {
        return rebootGeneration;
    }

    public Optional<DockerImage> getDockerImage() {
        return dockerImage;
    }

    public Optional<Version> getVespaVersion() {
        return vespaVersion;
    }

    public Optional<Version> getCurrentOsVersion() {
        return currentOsVersion;
    }

    public Optional<Instant> getCurrentFirmwareCheck() {
        return currentFirmwareCheck;
    }

    public Optional<Boolean> getWantToDeprovision() {
        return wantToDeprovision;
    }

    public Map<String, JsonNode> getReports() {
        return reports;
    }

    public Optional<TenantName> getReservedTo() {
        return reservedTo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(restartGeneration, rebootGeneration, dockerImage, vespaVersion, currentOsVersion,
                            currentFirmwareCheck, wantToDeprovision, reports, reservedTo);
    }

    public boolean isEmpty() {
        return equals(new NodeAttributes());
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof NodeAttributes)) {
            return false;
        }
        final NodeAttributes other = (NodeAttributes) o;

        return Objects.equals(restartGeneration, other.restartGeneration)
                && Objects.equals(rebootGeneration, other.rebootGeneration)
                && Objects.equals(dockerImage, other.dockerImage)
                && Objects.equals(vespaVersion, other.vespaVersion)
                && Objects.equals(currentOsVersion, other.currentOsVersion)
                && Objects.equals(currentFirmwareCheck, other.currentFirmwareCheck)
                && Objects.equals(reports, other.reports)
                && Objects.equals(reservedTo, other.reservedTo)
                && Objects.equals(wantToDeprovision, other.wantToDeprovision);
    }

    @Override
    public String toString() {
        return Stream.of(
                        restartGeneration.map(gen -> "restartGeneration=" + gen),
                        rebootGeneration.map(gen -> "rebootGeneration=" + gen),
                        dockerImage.map(img -> "dockerImage=" + img.asString()),
                        vespaVersion.map(ver -> "vespaVersion=" + ver.toFullString()),
                        currentOsVersion.map(ver -> "currentOsVersion=" + ver.toFullString()),
                        currentFirmwareCheck.map(at -> "currentFirmwareCheck=" + at),
                        Optional.ofNullable(reports.isEmpty() ? null : "reports=" + reports),
                        Optional.ofNullable(reservedTo.isEmpty() ? null : "reservedTo=" + reservedTo),
                        wantToDeprovision.map(depr -> "wantToDeprovision=" + depr))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
