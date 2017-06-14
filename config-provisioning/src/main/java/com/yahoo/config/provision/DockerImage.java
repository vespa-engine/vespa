// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

/**
 * A Docker image.
 *
 * @author mpolden
 */
public class DockerImage {

    private final String name;

    public DockerImage(String name) {
        this.name = name;
    }

    /** Get Docker image tag as version */
    public Version tagAsVersion() {
        String[] parts = asString().split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Could not parse tag from Docker image '" + asString() + "'");
        }
        return Version.fromString(parts[parts.length - 1]);
    }

    /** Returns the Docker image tagged with the given version */
    public DockerImage withTag(Version version) {
        return new DockerImage(name + ":" + version.toFullString());
    }

    public String asString() {
        return name;
    }

}
