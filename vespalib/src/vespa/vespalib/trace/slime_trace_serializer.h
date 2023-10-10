// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/trace/tracevisitor.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <stack>

namespace vespalib {

class TraceNode;

/**
 * A serializer of TraceNodes using the TraceVisitor API. The serialized order of the nodes are NOT
 * guaranteed to be in the same order as the original.
 */
class SlimeTraceSerializer : public TraceVisitor
{
public:
    SlimeTraceSerializer(slime::Cursor & cursor);
    void visit(const TraceNode & node) override;
    static const Memory TIMESTAMP;
    static const Memory PAYLOAD;
    static const Memory CHILDREN;
private:
    static void addTimestamp(slime::Cursor & current, const TraceNode & node);
    static void addPayload(slime::Cursor & current, const TraceNode & node);
    void addChildrenCursors(slime::Cursor & current, const TraceNode & node);
    void addChildrenCursorsToStack(slime::Cursor & childrenArray, const TraceNode & node);
    std::stack<slime::Cursor *> _cursors;
};

}
