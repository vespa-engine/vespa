// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.DockerImage;

import java.util.Objects;


// It somewhat sucks that this class almost duplicates a binding class used by NodeRepositoryImpl,
// but using the binding class here would be a layer violation, and would also tie this logic to
// serialization-related dependencies it needs not have.
class NodeAttributes {
    public final long restartGeneration;
    public final DockerImage dockerImage;
    public final String vespaVersion;

    NodeAttributes(long restartGeneration, DockerImage dockerImage, String vespaVersion) {
        this.restartGeneration = restartGeneration;
        this.dockerImage = dockerImage;
        this.vespaVersion = vespaVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(restartGeneration, dockerImage, vespaVersion);
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof NodeAttributes)) {
            return false;
        }
        final NodeAttributes other = (NodeAttributes) o;

        return Objects.equals(restartGeneration, other.restartGeneration)
                && Objects.equals(dockerImage, other.dockerImage)
                && Objects.equals(vespaVersion, other.vespaVersion);
    }

    @Override
    public String toString() {
        return "NodeAttributes{" +
                "restartGeneration=" + restartGeneration +
                ", dockerImage=" + dockerImage +
                ", vespaVersion='" + vespaVersion + '\'' +
                '}';
    }
}
