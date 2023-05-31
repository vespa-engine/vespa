// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search::expression {

class DebugWaitFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(DebugWaitFunctionNode);
    DECLARE_NBO_SERIALIZE;
    DebugWaitFunctionNode();
    ~DebugWaitFunctionNode() override;
    DebugWaitFunctionNode(ExpressionNode::UP arg, double waitTime, bool busyWait);
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
private:
    bool onExecute() const override;
    double _waitTime;
    bool   _busyWait;
};

}
