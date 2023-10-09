// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search {
namespace expression {

class ToStringFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(ToStringFunctionNode);
    ToStringFunctionNode() { }
    ToStringFunctionNode(ExpressionNode::UP arg) : UnaryFunctionNode(std::move(arg)) { }
private:
    bool onExecute() const override;
    void onPrepareResult() override;
};

}
}

