// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"

namespace search::expression {

class CatFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(CatFunctionNode);
    CatFunctionNode() { }
    CatFunctionNode(ExpressionNode::UP arg) { addArg(std::move(arg)); }
private:
    void onPrepare(bool preserveAccurateTypes) override;
    void onPrepareResult() override;
    bool onExecute() const override;
};

}
