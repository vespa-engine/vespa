// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericfunctionnode.h"

namespace search {
namespace expression {

class MaxFunctionNode : public NumericFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(MaxFunctionNode);
    MaxFunctionNode() { }
private:
    void onArgument(const ResultNode & arg, ResultNode & result) const override;
    ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const override { return v.flattenMax(result); }
    ResultNode::CP getInitialValue() const override;
};

}
}

