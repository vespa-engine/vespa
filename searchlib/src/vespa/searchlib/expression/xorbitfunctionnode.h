// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unarybitfunctionnode.h>

namespace search {
namespace expression {

class XorBitFunctionNode : public UnaryBitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(XorBitFunctionNode);
    XorBitFunctionNode() { }
    XorBitFunctionNode(const ExpressionNode::CP & arg, unsigned numBits);
private:
    mutable std::vector<uint8_t> _tmpXor;
    virtual bool internalExecute(const vespalib::nbostream & os) const;
    virtual void onPrepareResult();
};

}
}

