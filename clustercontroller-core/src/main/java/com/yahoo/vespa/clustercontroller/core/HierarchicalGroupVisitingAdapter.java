// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.GroupVisitor;

/**
 * Exposes {@link Distribution} as a {@link HierarchicalGroupVisiting}.
 *
 * @author hakon
 */
public class HierarchicalGroupVisitingAdapter implements HierarchicalGroupVisiting {
    private final Distribution distribution;

    public HierarchicalGroupVisitingAdapter(Distribution distribution) {
        this.distribution = distribution;
    }

    @Override
    public void visit(GroupVisitor visitor) {
        if (distribution.getRootGroup().isLeafGroup()) {
            // A flat non-hierarchical cluster
            return;
        }

        distribution.visitGroups(group -> {
            if (group.isLeafGroup()) {
                return visitor.visitGroup(group);
            }

            return true;
        });
    }
}
