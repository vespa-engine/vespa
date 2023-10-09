// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/string.h>
#include <stack>

namespace vespalib {

class TraceNode;

/**
 * For deserializing a TraceNode and its children. Note that the ordering of nodes
 * are NOT guaranteed.
 */
class SlimeTraceDeserializer
{
public:
    SlimeTraceDeserializer(const slime::Inspector & inspector);
    TraceNode deserialize() const;
private:
    static TraceNode deserialize(const slime::Inspector & inspector);
    static TraceNode deserializeTraceNode(const slime::Inspector & inspector);
    static void deserializeChildren(const slime::Inspector & inspector, TraceNode & node);
    static bool hasPayload(const slime::Inspector & inspector);
    static int64_t decodeTimestamp(const slime::Inspector & inspector);
    static vespalib::string decodePayload(const slime::Inspector & inspector);
    const slime::Inspector & _inspector;
};

}
