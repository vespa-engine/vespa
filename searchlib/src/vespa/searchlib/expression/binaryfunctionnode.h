// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"

namespace search {
namespace expression {

class BinaryFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(BinaryFunctionNode);
    BinaryFunctionNode() { }
    BinaryFunctionNode(ExpressionNode::UP arg1, ExpressionNode::UP arg2) :
        MultiArgFunctionNode()
    {
        appendArg(std::move(arg1));
        appendArg(std::move(arg2));
    }
};

}
}

