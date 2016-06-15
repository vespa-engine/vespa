// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unarybitfunctionnode.h>

namespace search {
namespace expression {

class MD5BitFunctionNode : public UnaryBitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(MD5BitFunctionNode);
    MD5BitFunctionNode() { }
    MD5BitFunctionNode(const ExpressionNode::CP & arg, unsigned numBits) : UnaryBitFunctionNode(arg, numBits) { }
private:
    virtual bool internalExecute(const vespalib::nbostream & os) const;
};

}
}

