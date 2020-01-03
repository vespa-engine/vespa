// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * @author freva
 */
@FunctionalInterface
public interface NodeAgentFactory {
    NodeAgent create(NodeAgentContextSupplier contextSupplier, NodeAgentContext nodeAgentContext);
}
