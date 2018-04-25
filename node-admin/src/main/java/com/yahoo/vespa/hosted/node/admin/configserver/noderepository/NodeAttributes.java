// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.dockerapi.DockerImage;

import java.util.Objects;
import java.util.Optional;

public class NodeAttributes {

    private Optional<Long> restartGeneration = Optional.empty();
    private Optional<Long> rebootGeneration = Optional.empty();
    private Optional<DockerImage> dockerImage = Optional.empty();
    private Optional<String> hardwareDivergence = Optional.empty();

    public NodeAttributes() { }

    public NodeAttributes withRestartGeneration(Optional<Long> restartGeneration) {
        this.restartGeneration = restartGeneration;
        return this;
    }

    public NodeAttributes withRebootGeneration(long rebootGeneration) {
        this.rebootGeneration = Optional.of(rebootGeneration);
        return this;
    }

    public NodeAttributes withDockerImage(DockerImage dockerImage) {
        this.dockerImage = Optional.of(dockerImage);
        return this;
    }

    public NodeAttributes withHardwareDivergence(String hardwareDivergence) {
        this.hardwareDivergence = Optional.of(hardwareDivergence);
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

    public Optional<String> getHardwareDivergence() {
        return hardwareDivergence;
    }

    @Override
    public int hashCode() {
        return Objects.hash(restartGeneration, rebootGeneration, dockerImage, hardwareDivergence);
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
                && Objects.equals(hardwareDivergence, other.hardwareDivergence);
    }

    @Override
    public String toString() {
        return "NodeAttributes{" +
                "restartGeneration=" + restartGeneration.map(String::valueOf).orElse("") +
                ", rebootGeneration=" + rebootGeneration.map(String::valueOf).orElse("") +
                ", dockerImage=" + dockerImage.map(DockerImage::asString).orElse("") +
                ", hardwareDivergence='" + hardwareDivergence.orElse(null) + "'" +
                '}';
    }
}
