// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.vespa.hosted.node.admin.NodeRepositoryNode;

/**
 * @author hmusum
 */
public class DefaultContainerEnvironmentResolver implements ContainerEnvironmentResolver {

    public String createSettings(Environment environment, NodeRepositoryNode node) {
        return new ContainerEnvironmentSettings()
                .set("configServerAddresses", environment.getConfigServerHostNames())
                .set("nodeType", node.nodeType)
                .set("cloud", environment.getCloud())
                .build();
    }

}
