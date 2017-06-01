// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "debugwaitfunctionnode.h"
#include <vespa/fastos/time.h>
#include <thread>

namespace search {
namespace expression {

using vespalib::FieldBase;
using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(DebugWaitFunctionNode, UnaryFunctionNode);

DebugWaitFunctionNode::DebugWaitFunctionNode()
    : _waitTime(0.0),
      _busyWait(true)
{ }

DebugWaitFunctionNode::~DebugWaitFunctionNode()
{
}

DebugWaitFunctionNode::DebugWaitFunctionNode(ExpressionNode::UP arg, double waitTime, bool busyWait)
    : UnaryFunctionNode(std::move(arg)),
      _waitTime(waitTime),
      _busyWait(busyWait)
{
}

bool
DebugWaitFunctionNode::onExecute() const
{
    FastOS_Time time;
    time.SetNow();
    double millis = _waitTime * 1000.0;

    while (time.MilliSecsToNow() < millis) {
        if (_busyWait) {
            for (int i = 0; i < 1000; i++)
                ;
        } else {
            int rem = (int)(millis - time.MilliSecsToNow());
            std::this_thread::sleep_for(std::chrono::milliseconds(rem));
        }
    }
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
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_debugwaitfunctionnode() {}
