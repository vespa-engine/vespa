// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_bounded_trace.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace storage {

MemoryBoundedTrace::MemoryBoundedTrace(size_t softMemoryUpperBound)
    : _trace(),
      _currentMemoryUsed(0),
      _omittedNodes(0),
      _omittedBytes(0),
      _softMemoryUpperBound(softMemoryUpperBound)
{
}

bool
MemoryBoundedTrace::add(const mbus::TraceNode& node)
{
    const size_t nodeFootprint = node.computeMemoryUsage();

    if (_currentMemoryUsed >= _softMemoryUpperBound) {
        ++_omittedNodes;
        _omittedBytes += nodeFootprint;
        return false;
    }
    _trace.addChild(vespalib::TraceNode(node));
    _currentMemoryUsed += nodeFootprint;
    return true;
}

bool
MemoryBoundedTrace::add(mbus::Trace && node)
{
    const size_t nodeFootprint = node.computeMemoryUsage();

    if (_currentMemoryUsed >= _softMemoryUpperBound) {
        ++_omittedNodes;
        _omittedBytes += nodeFootprint;
        return false;
    }
    _trace.addChild(std::move(node));
    _currentMemoryUsed += nodeFootprint;
    return true;
}

void
MemoryBoundedTrace::moveTraceTo(mbus::Trace& out)
{
    if (_trace.isEmpty()) {
        return;
    }
    if (_omittedNodes > 0) {
        _trace.trace(0, vespalib::make_string(
                "Trace too large; omitted %zu subsequent trace trees "
                "containing a total of %zu bytes",
                _omittedNodes, _omittedBytes), false);
    }
    out.addChild(std::move(_trace));
    _currentMemoryUsed = 0;
    _omittedNodes = 0;
    _omittedBytes = 0;
}

} // storage

