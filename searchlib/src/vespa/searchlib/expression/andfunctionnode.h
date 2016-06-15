// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/bitfunctionnode.h>
#include <vespa/searchlib/expression/integerresultnode.h>

namespace search {
namespace expression {

class AndFunctionNode : public BitFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(AndFunctionNode);
    AndFunctionNode() { }
private:
    virtual ResultNode::CP getInitialValue() const { return  ResultNode::CP(new Int64ResultNode(-1)); }
    virtual ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const { return v.flattenAnd(result); }

    virtual void onArgument(const ResultNode & arg, Int64ResultNode & result) const;
};

}
}

