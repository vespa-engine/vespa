// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.DockerImage;

import java.util.Objects;


// It somewhat sucks that this class almost duplicates a binding class used by NodeRepositoryImpl,
// but using the binding class here would be a layer violation, and would also tie this logic to
// serialization-related dependencies it needs not have.
public class NodeAttributes {
    private Long restartGeneration = null;
    private Long rebootGeneration = null;
    private DockerImage dockerImage = null;
    private String vespaVersion = null;

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

    @Override
    public int hashCode() {
        return Objects.hash(restartGeneration, rebootGeneration, dockerImage, vespaVersion);
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
                && Objects.equals(vespaVersion, other.vespaVersion);
    }

    @Override
    public String toString() {
        return "NodeAttributes{" +
                "restartGeneration=" + restartGeneration +
                ", rebootGeneration=" + rebootGeneration +
                ", dockerImage=" + dockerImage.asString() +
                ", vespaVersion='" + vespaVersion + '\'' +
                '}';
    }
}
