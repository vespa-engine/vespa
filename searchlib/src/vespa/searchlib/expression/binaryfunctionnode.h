// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/multiargfunctionnode.h>

namespace search {
namespace expression {

class BinaryFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(BinaryFunctionNode);
    BinaryFunctionNode() { }
    BinaryFunctionNode(const ExpressionNode::CP & arg1, const ExpressionNode::CP & arg2) :
        MultiArgFunctionNode()
    {
        appendArg(arg1);
        appendArg(arg2);
    }
};

}
}

