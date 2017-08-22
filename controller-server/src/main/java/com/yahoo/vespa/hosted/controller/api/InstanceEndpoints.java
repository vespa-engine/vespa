// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api;

import java.net.URI;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public class InstanceEndpoints {

    private final List<URI> containerEndpoints;

    public InstanceEndpoints(List<URI> containerEndpoints) {
        this.containerEndpoints = containerEndpoints;
    }

    public List<URI> getContainerEndpoints() {
        return containerEndpoints;
    }
}


