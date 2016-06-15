// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unaryfunctionnode.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>


namespace search {
namespace expression {

class DebugWaitFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(DebugWaitFunctionNode);
    DECLARE_NBO_SERIALIZE;
    DebugWaitFunctionNode();
    ~DebugWaitFunctionNode();
    DebugWaitFunctionNode(const ExpressionNode::CP & arg, double waitTime, bool busyWait);
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
private:
    virtual bool onExecute() const;
    double _waitTime;
    bool   _busyWait;
};

}
}

