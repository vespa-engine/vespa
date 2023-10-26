// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unarybitfunctionnode.h"

namespace search::expression {

class XorBitFunctionNode : public UnaryBitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(XorBitFunctionNode);
    XorBitFunctionNode();
    XorBitFunctionNode(ExpressionNode::UP arg, unsigned numBits);
    ~XorBitFunctionNode();
private:
    mutable std::vector<uint8_t> _tmpXor;
    bool internalExecute(const vespalib::nbostream & os) const override;
    void onPrepareResult() override;
};

}
