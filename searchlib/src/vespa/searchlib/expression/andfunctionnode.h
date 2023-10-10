// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bitfunctionnode.h"
#include "integerresultnode.h"

namespace search {
namespace expression {

class AndFunctionNode : public BitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(AndFunctionNode);
    AndFunctionNode() { }
private:
    ResultNode::CP getInitialValue() const override { return  ResultNode::CP(new Int64ResultNode(-1)); }
    ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const override { return v.flattenAnd(result); }
    void onArgument(const ResultNode & arg, Int64ResultNode & result) const override;
};

}
}
