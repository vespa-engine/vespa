// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_bounded_trace.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace storage {

MemoryBoundedTrace::MemoryBoundedTrace(size_t softMemoryUpperBound)
    : _node(),
      _currentMemoryUsed(0),
      _omittedNodes(0),
      _omittedBytes(0),
      _softMemoryUpperBound(softMemoryUpperBound)
{
}

namespace {

size_t
computeTraceTreeMemoryUsage(const mbus::TraceNode& node)
{
    if (node.isLeaf()) {
        return node.getNote().size();
    }
    size_t childSum = 0;
    const uint32_t childCount = node.getNumChildren();
    for (uint32_t i = 0; i < childCount; ++i) {
        childSum += computeTraceTreeMemoryUsage(node.getChild(i));
    }
    return childSum;
}

} // anon ns

bool
MemoryBoundedTrace::add(const mbus::Trace& trace)
{
    if (trace.isEmpty()) return false;
    const vespalib::TraceNode & node = trace.getRoot();
    const size_t nodeFootprint = computeTraceTreeMemoryUsage(node);

    if (_currentMemoryUsed >= _softMemoryUpperBound) {
        ++_omittedNodes;
        _omittedBytes += nodeFootprint;
        return false;
    }
    _node.addChild(node);
    _currentMemoryUsed += nodeFootprint;
    return true;
}

void
MemoryBoundedTrace::moveTraceTo(mbus::Trace& out)
{
    if (_node.isEmpty()) {
        return;
    }
    if (_omittedNodes > 0) {
        _node.addChild(vespalib::make_string(
                "Trace too large; omitted %zu subsequent trace trees "
                "containing a total of %zu bytes",
                _omittedNodes, _omittedBytes));
    }
    out.addChild(std::move(_node)); // XXX rvalue support should be added to TraceNode.
    _node.clear();
    _currentMemoryUsed = 0;
    _omittedNodes = 0;
    _omittedBytes = 0;
}

} // storage

