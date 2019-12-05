// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/trace/slime_trace_deserializer.h>
#include <vespa/vespalib/trace/slime_trace_serializer.h>
#include <vespa/vespalib/trace/tracenode.h>

using namespace vespalib::slime;

namespace vespalib {

SlimeTraceDeserializer::SlimeTraceDeserializer(const Inspector & inspector)
    : _inspector(inspector)
{
}

TraceNode
SlimeTraceDeserializer::deserialize() const
{
    return deserialize(_inspector);
}

TraceNode
SlimeTraceDeserializer::deserialize(const Inspector & inspector)
{
    TraceNode node(deserializeTraceNode(inspector));
    deserializeChildren(inspector[SlimeTraceSerializer::CHILDREN], node);
    return node;
}

TraceNode
SlimeTraceDeserializer::deserializeTraceNode(const Inspector & inspector)
{
    system_time timestamp(std::chrono::milliseconds(decodeTimestamp(inspector)));
    if (hasPayload(inspector)) {
        std::string note(decodePayload(inspector));
        return TraceNode(note, timestamp);
    }
    return TraceNode(timestamp);
}

bool
SlimeTraceDeserializer::hasPayload(const Inspector & inspector)
{
    return inspector[SlimeTraceSerializer::PAYLOAD].valid();
}

vespalib::string
SlimeTraceDeserializer::decodePayload(const Inspector & inspector)
{
    return inspector[SlimeTraceSerializer::PAYLOAD].asString().make_string();
}

int64_t
SlimeTraceDeserializer::decodeTimestamp(const Inspector & inspector)
{
    return inspector[SlimeTraceSerializer::TIMESTAMP].asLong();
}

void
SlimeTraceDeserializer::deserializeChildren(const Inspector & inspector, TraceNode & node)
{
    for (size_t i(0); i < inspector.children(); i++) {
        Inspector & child(inspector[i]);
        TraceNode childNode(deserialize(child));
        node.addChild(childNode);
    }
}

}
