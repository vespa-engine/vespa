// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bitfunctionnode.h"

namespace search {
namespace expression {

class XorFunctionNode : public BitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(XorFunctionNode);
    XorFunctionNode() { }
private:
    ResultNode::CP getInitialValue() const override { return  ResultNode::CP(new Int64ResultNode(0)); }
    ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const override { return v.flattenXor(result); }
    void onArgument(const ResultNode & arg, Int64ResultNode & result) const override;
};

}
}

