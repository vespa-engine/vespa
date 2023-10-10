// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Group;

import java.util.ArrayList;
import java.util.List;

public class LeafGroups {

    /**
     * Return a list of all groups that do not themselves have any child groups,
     * i.e. only the groups that contain nodes.
     *
     * The output order is not defined.
     */
    public static List<Group> enumerateFrom(Group root) {
        List<Group> leaves = new ArrayList<>();
        visitNode(root, leaves);
        return leaves;
    }

    private static void visitNode(Group node, List<Group> leaves) {
        if (node.isLeafGroup()) {
            leaves.add(node);
        } else {
            node.getSubgroups().forEach((idx, g) -> visitNode(g, leaves));
        }
    }

}
