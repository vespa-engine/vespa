// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;

/**
 * @author freva
 */
@FunctionalInterface
public interface NodeAgentContextFactory {
    NodeAgentContext create(NodeSpec nodeSpec, Acl acl);
}
