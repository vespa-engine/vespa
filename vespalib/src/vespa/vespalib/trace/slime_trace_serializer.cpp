// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "slime_trace_serializer.h"
#include "trace.h"
#include <cassert>

using namespace vespalib::slime;

namespace vespalib {

const Memory SlimeTraceSerializer::TIMESTAMP("timestamp");
const Memory SlimeTraceSerializer::PAYLOAD("payload");
const Memory SlimeTraceSerializer::CHILDREN("children");

SlimeTraceSerializer::SlimeTraceSerializer(Cursor & cursor)
    : _cursors()
{
    _cursors.push(&cursor);
}

void
SlimeTraceSerializer::visit(const TraceNode & node)
{
    assert(!_cursors.empty());
    Cursor * current(_cursors.top());
    assert(current != NULL);
    _cursors.pop();
    addTimestamp(*current, node);
    addPayload(*current, node);
    addChildrenCursors(*current, node);
}

void
SlimeTraceSerializer::addTimestamp(Cursor & current, const TraceNode & node)
{
    current.setLong(TIMESTAMP, count_ms(node.getTimestamp().time_since_epoch()));
}

void
SlimeTraceSerializer::addPayload(Cursor & current, const TraceNode & node)
{
    if (node.hasNote()) {
        current.setString(PAYLOAD, Memory(node.getNote()));
    }
}

void
SlimeTraceSerializer::addChildrenCursors(Cursor & current, const TraceNode & node)
{
    if (node.getNumChildren() > 0) {
        addChildrenCursorsToStack(current.setArray(CHILDREN), node);
    }
}

void
SlimeTraceSerializer::addChildrenCursorsToStack(Cursor & childrenArray, const TraceNode & node)
{
    for (uint32_t childIndex(0); childIndex < node.getNumChildren(); childIndex++) {
        Cursor & child(childrenArray.addObject());
        _cursors.push(&child);
    }
}

}
