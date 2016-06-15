// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/numericfunctionnode.h>

namespace search {
namespace expression {

class MultiplyFunctionNode : public NumericFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(MultiplyFunctionNode);
    MultiplyFunctionNode() { }
private:
    virtual void onArgument(const ResultNode & arg, ResultNode & result) const;
    virtual ResultNode::CP getInitialValue() const { return  ResultNode::CP(new Int64ResultNode(1)); }
    virtual ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const { return v.flattenMultiply(result); }
};

}
}

