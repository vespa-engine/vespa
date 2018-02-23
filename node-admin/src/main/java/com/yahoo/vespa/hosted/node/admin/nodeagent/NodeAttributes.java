// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.DockerImage;

import java.util.Objects;


// It somewhat sucks that this class almost duplicates a binding class used by RealNodeRepository,
// but using the binding class here would be a layer violation, and would also tie this logic to
// serialization-related dependencies it needs not have.
public class NodeAttributes {
    private Long restartGeneration = null;
    private Long rebootGeneration = null;
    private DockerImage dockerImage = null;
    private String vespaVersion = null;
    private String hardwareDivergence = null;

    public NodeAttributes() { }

    public NodeAttributes withRestartGeneration(Long restartGeneration) {
        this.restartGeneration = restartGeneration;
        return this;
    }

    public NodeAttributes withRebootGeneration(Long rebootGeneration) {
        this.rebootGeneration = rebootGeneration;
        return this;
    }

    public NodeAttributes withDockerImage(DockerImage dockerImage) {
        this.dockerImage = dockerImage;
        return this;
    }

    public NodeAttributes withVespaVersion(String vespaVersion) {
        this.vespaVersion = vespaVersion;
        return this;
    }

    public NodeAttributes withHardwareDivergence(String hardwareDivergence) {
        this.hardwareDivergence = hardwareDivergence;
        return this;
    }

    public Long getRestartGeneration() {
        return restartGeneration;
    }

    public Long getRebootGeneration() {
        return rebootGeneration;
    }

    public DockerImage getDockerImage() {
        return dockerImage;
    }

    public String getVespaVersion() {
        return vespaVersion;
    }

    public String getHardwareDivergence() {
        return hardwareDivergence;
    }

    @Override
    public int hashCode() {
        return Objects.hash(restartGeneration, rebootGeneration, dockerImage, vespaVersion, hardwareDivergence);
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
                && Objects.equals(hardwareDivergence, other.hardwareDivergence);
    }

    @Override
    public String toString() {
        return "NodeAttributes{" +
                "restartGeneration=" + restartGeneration +
                ", rebootGeneration=" + rebootGeneration +
                ", dockerImage=" + dockerImage.asString() +
                ", vespaVersion='" + vespaVersion + '\'' +
                ", hardwareDivergence='" + hardwareDivergence + '\'' +
                '}';
    }
}
