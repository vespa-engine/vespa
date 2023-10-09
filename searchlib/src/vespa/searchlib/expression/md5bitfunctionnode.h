// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unarybitfunctionnode.h"

namespace search {
namespace expression {

class MD5BitFunctionNode : public UnaryBitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(MD5BitFunctionNode);
    MD5BitFunctionNode() { }
    MD5BitFunctionNode(ExpressionNode::UP arg, unsigned numBits) : UnaryBitFunctionNode(std::move(arg), numBits) { }
private:
    bool internalExecute(const vespalib::nbostream & os) const override;
};

}
}

