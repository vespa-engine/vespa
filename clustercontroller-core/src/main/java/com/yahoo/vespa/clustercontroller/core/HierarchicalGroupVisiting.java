// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.GroupVisitor;

class HierarchicalGroupVisiting {

    private final Distribution distribution;

    public HierarchicalGroupVisiting(Distribution distribution) {
        this.distribution = distribution;
    }

    /**
     * Returns true if the group contains more than one (leaf) group.
     */
    public boolean isHierarchical() {
        return !distribution.getRootGroup().isLeafGroup();
    }

    /**
     * Invoke the visitor for each leaf group of an implied group.  If the group is non-hierarchical
     * (flat), the visitor will not be invoked.
     */
    public void visit(GroupVisitor visitor) {
        if (isHierarchical()) {
            distribution.visitGroups(group -> {
                if (group.isLeafGroup()) {
                    return visitor.visitGroup(group);
                }

                return true;
            });
        }
    }

}
