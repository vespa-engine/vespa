// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.net.URI;

/**
 * @author freva
 */
public class DockerRegistryCredentials {
    public final URI registry;
    public final String username;
    public final String password;

    public DockerRegistryCredentials(URI registry, String username, String password) {
        this.registry = registry;
        this.username = username;
        this.password = password;
    }
}
