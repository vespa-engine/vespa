// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/trace.h>

namespace storage {

class MemoryBoundedTrace {
public:
    MemoryBoundedTrace(size_t softMemoryUpperBound);
    /**
     * Attempt to append the given trace node to the internal trace tree.
     * If the amount of memory currently being used exceeds that of the upper
     * bound used when constructing `this`, the node will not be added to
     * the tree. Note that this only takes place on the granularity of full
     * trees; either the entire trace tree given by `node` is added or nothing
     * at all. This means it's possible to exceed the upper bound if the node
     * is sufficiently large when added before memory has hit the limit; only
     * subsequent adds will fail.
     *
     * Returns true if `node` was added to internal trace state, false
     * otherwise.
     */
    bool add(const mbus::TraceNode& node);
    bool add(mbus::Trace && trace);

    /**
     * Append current trace tree to the output trace node and clear internal
     * tree in the process. In the case that at least 1 node has been
     * omitted due to memory bounds being exceeded, the trace will contain a
     * node at its end detailing the number of traces and bytes that have been
     * omitted from the output.
     *
     * If current trace is empty, no nodes are added to `out`.
     */
    void moveTraceTo(mbus::Trace& out);

    size_t getApproxMemoryUsed() const noexcept {
        return _currentMemoryUsed;
    }

private:
    mbus::Trace _trace;
    size_t _currentMemoryUsed;
    size_t _omittedNodes;
    size_t _omittedBytes;
    size_t _softMemoryUpperBound;
};

} // storage
