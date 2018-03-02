// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Optional;

/**
 * @author freva
 */
public interface DockerRegistryCredentialsSupplier {

    /**
     * Returns credentials to docker registry needed to be able to pull/push given
     * docker image.
     */
    Optional<DockerRegistryCredentials> getCredentials(DockerImage dockerImage);
}
