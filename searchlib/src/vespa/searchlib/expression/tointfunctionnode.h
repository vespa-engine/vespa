// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unaryfunctionnode.h>

namespace search {
namespace expression {

class ToIntFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(ToIntFunctionNode);
    ToIntFunctionNode() { }
    ToIntFunctionNode(const ExpressionNode::CP & arg) : UnaryFunctionNode(arg) { }
private:
    virtual bool onExecute() const;
    virtual void onPrepareResult();
};

}
}

