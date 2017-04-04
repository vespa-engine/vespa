// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

/**
 * A Docker image.
 *
 * @author mpolden
 */
public class DockerImage {

    public static final DockerImage defaultImage = new DockerImage("docker-registry.ops.yahoo.com:4443/vespa/ci");

    private final String name;

    public DockerImage(String name) {
        this.name = name;
    }

    /** Get Docker image tag as version */
    public Version tagAsVersion() {
        String[] parts = toString().split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Could not parse tag from Docker image '" + toString() + "'");
        }
        return Version.fromString(parts[parts.length - 1]);
    }

    /** Returns the Docker image tagged with the given version */
    public DockerImage withTag(Version version) {
        return new DockerImage(name + ":" + version.toFullString());
    }

    @Override
    public String toString() {
        return name;
    }
}
