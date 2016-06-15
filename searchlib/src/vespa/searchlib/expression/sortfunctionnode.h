// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unaryfunctionnode.h>

namespace search {
namespace expression {

class SortFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(SortFunctionNode);
    SortFunctionNode() { }
    SortFunctionNode(const ExpressionNode::CP & arg) : UnaryFunctionNode(arg) { }
private:
    virtual bool onExecute() const;
};

}
}

