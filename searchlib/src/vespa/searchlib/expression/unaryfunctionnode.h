// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"

namespace search::expression {

class UnaryFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(UnaryFunctionNode);
    UnaryFunctionNode() noexcept = default;
    UnaryFunctionNode(ExpressionNode::UP arg) :
        MultiArgFunctionNode()
    {
        appendArg(std::move(arg));
    }
protected:
    const ExpressionNode & getArg() const { return MultiArgFunctionNode::getArg(0); }
private:
    void onPrepareResult() override;
};

}
