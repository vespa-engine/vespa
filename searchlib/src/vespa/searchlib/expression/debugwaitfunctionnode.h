// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"
#include <vespa/searchlib/common/sortspec.h>
#include "stringresultnode.h"
#include "resultvector.h"


namespace search {
namespace expression {

class DebugWaitFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(DebugWaitFunctionNode);
    DECLARE_NBO_SERIALIZE;
    DebugWaitFunctionNode();
    ~DebugWaitFunctionNode();
    DebugWaitFunctionNode(ExpressionNode::UP arg, double waitTime, bool busyWait);
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
private:
    virtual bool onExecute() const;
    double _waitTime;
    bool   _busyWait;
};

}
}

