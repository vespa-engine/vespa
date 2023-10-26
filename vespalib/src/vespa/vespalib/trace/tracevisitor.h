// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

class TraceNode;

/**
 * This class is an abstract visitor of {@link TraceNode}. See {@link TraceNode#accept(TraceVisitor)}.
 */
struct TraceVisitor {
    virtual ~TraceVisitor() { }
    /**
     * Visits a {@link TraceNode}. Called by {@link TraceNode#accept(TraceVisitor)}, before visiting its children.
     *
     * @param node the TraceNode being visited
     * @see TraceNode#accept(TraceVisitor)
     */
    virtual void visit(const TraceNode & node) = 0;

    /**
     * Enters a {@link TraceNode}. This method is called after {@link #visit(TraceNode)}, but before visiting its children.
     * Note that this method is NOT called if TraceNode has zero children. The default implementation of this method does nothing.
     *
     * @param node The TraceNode being entered
     * @see #leaving(TraceNode)
     */
    virtual void entering(const TraceNode & node) { (void) node; }

    /**
     * Leaves a {@link TraceNode}. This method is called after {@link #entering(TraceNode)}, and after visiting its
     * children. Note that this method is NOT called if TraceNode has zero children.
     * The default implementation of this method does nothing.
     *
     * @param node the TraceNode being left
     */
    virtual void leaving(const TraceNode & node) { (void) node; }
};

} // namespace vespalib

