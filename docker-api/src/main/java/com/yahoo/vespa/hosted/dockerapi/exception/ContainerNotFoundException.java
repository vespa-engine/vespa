// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.exception;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;

/**
 * @author freva
 */
public class ContainerNotFoundException extends DockerException {
    public ContainerNotFoundException(ContainerName containerName) {
        super("No such container: " + containerName.asString());
    }
}
