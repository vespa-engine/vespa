// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class NodeStateTest {

    @Test
    public void is_equal_to_node_repository_states() {
        Set<String> nodeRepositoryStates = Stream.of(Node.State.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> nodeAdminStates = Stream.of(NodeState.values()).map(Enum::name).collect(Collectors.toSet());

        assertEquals(nodeAdminStates, nodeRepositoryStates);
    }

}