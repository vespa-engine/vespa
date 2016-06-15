// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/multiargfunctionnode.h>

namespace search {
namespace expression {

class UnaryFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(UnaryFunctionNode);
    UnaryFunctionNode() { }
    UnaryFunctionNode(const ExpressionNode::CP & arg) :
        MultiArgFunctionNode()
    {
        appendArg(arg);
    }
protected:
    const ExpressionNode & getArg() const { return MultiArgFunctionNode::getArg(0); }
private:
    virtual void onPrepareResult();
};

}
}

