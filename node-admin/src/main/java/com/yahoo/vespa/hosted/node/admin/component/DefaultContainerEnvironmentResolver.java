// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

/**
 * @author hmusum
 */
public class DefaultContainerEnvironmentResolver implements ContainerEnvironmentResolver {

    public String createSettings(Environment environment, ContainerNodeSpec nodeSpec) {
        return new ContainerEnvironmentSettings()
                .set("configServerAddresses", environment.getConfigServerHostNames())
                .set("nodeType", nodeSpec.nodeType)
                .set("cloud", environment.getCloud())
                .build();
    }

}
