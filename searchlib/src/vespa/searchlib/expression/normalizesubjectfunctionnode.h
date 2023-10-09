// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search {
namespace expression {

class NormalizeSubjectFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(NormalizeSubjectFunctionNode);
    NormalizeSubjectFunctionNode() { }
    NormalizeSubjectFunctionNode(ExpressionNode::UP arg) : UnaryFunctionNode(std::move(arg)) { }
private:
    bool onExecute() const override;
    void onPrepareResult() override;
};

}
}

