// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.GroupVisitor;

@FunctionalInterface
public interface HierarchicalGroupVisiting {
    /**
     * Invoke the visitor for each leaf group of an implied group.  If the group is non-hierarchical
     * (flat), the visitor will not be invoked.
     */
    void visit(GroupVisitor visitor);
}
