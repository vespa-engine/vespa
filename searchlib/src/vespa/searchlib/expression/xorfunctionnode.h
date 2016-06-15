// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/bitfunctionnode.h>

namespace search {
namespace expression {

class XorFunctionNode : public BitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(XorFunctionNode);
    XorFunctionNode() { }
private:
    virtual ResultNode::CP getInitialValue() const { return  ResultNode::CP(new Int64ResultNode(0)); }
    virtual ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const { return v.flattenXor(result); }
    virtual void onArgument(const ResultNode & arg, Int64ResultNode & result) const;
};

}
}

