// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericfunctionnode.h"

namespace search {
namespace expression {

class AddFunctionNode : public NumericFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(AddFunctionNode);
    AddFunctionNode() { }
private:
    void onArgument(const ResultNode & arg, ResultNode & result) const override;
    ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const override { return v.flattenSum(result); }
    ResultNode::CP getInitialValue() const override { return  ResultNode::CP(new Int64ResultNode(0)); }
};

}
}

