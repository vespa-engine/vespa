// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"

namespace search {
namespace expression {

class StrCatFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(StrCatFunctionNode);
    StrCatFunctionNode() { }
    StrCatFunctionNode(ExpressionNode::UP arg) { addArg(std::move(arg)); }
private:
    void onPrepareResult() override;
    bool onExecute() const override;
};

}
}

