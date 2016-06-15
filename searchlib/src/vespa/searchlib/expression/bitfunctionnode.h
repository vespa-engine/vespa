// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/multiargfunctionnode.h>
#include <vespa/searchlib/expression/integerresultnode.h>

namespace search {
namespace expression {

class BitFunctionNode : public NumericFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(BitFunctionNode);
    BitFunctionNode() { }
protected:
    virtual void onPrepareResult();
private:
    virtual void onArgument(const ResultNode & arg, Int64ResultNode & result) const = 0;
    virtual void onArgument(const ResultNode & arg, ResultNode & result) const;
};

}
}

