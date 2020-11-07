// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "debugwaitfunctionnode.h"
#include <vespa/vespalib/util/time.h>

namespace search::expression {

using vespalib::Serializer;
using vespalib::Deserializer;
using namespace std::chrono;

IMPLEMENT_EXPRESSIONNODE(DebugWaitFunctionNode, UnaryFunctionNode);

DebugWaitFunctionNode::DebugWaitFunctionNode()
    : _waitTime(0.0),
      _busyWait(true)
{ }

DebugWaitFunctionNode::~DebugWaitFunctionNode() = default;

DebugWaitFunctionNode::DebugWaitFunctionNode(ExpressionNode::UP arg, double waitTime, bool busyWait)
    : UnaryFunctionNode(std::move(arg)),
      _waitTime(waitTime),
      _busyWait(busyWait)
{
}

using std::chrono::microseconds;

bool
DebugWaitFunctionNode::onExecute() const
{
    vespalib::Timer::waitAtLeast(vespalib::from_s(_waitTime), _busyWait);

    getArg().execute();
    updateResult().assign(getArg().getResult());
    return true;
}

Serializer &
DebugWaitFunctionNode::onSerialize(Serializer & os) const
{
    UnaryFunctionNode::onSerialize(os);
    return os << _waitTime << _busyWait;
}

Deserializer &
DebugWaitFunctionNode::onDeserialize(Deserializer & is)
{
    UnaryFunctionNode::onDeserialize(is);
    is >> _waitTime >> _busyWait;
    return is;
}

void
DebugWaitFunctionNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    UnaryFunctionNode::visitMembers(visitor);
    visit(visitor, "waitTime", _waitTime);
    visit(visitor, "busyWait", _busyWait);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_debugwaitfunctionnode() {}
