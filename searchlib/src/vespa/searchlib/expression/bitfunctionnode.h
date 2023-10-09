// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"
#include "integerresultnode.h"

namespace search {
namespace expression {

class BitFunctionNode : public NumericFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(BitFunctionNode);
    BitFunctionNode() { }
protected:
    void onPrepareResult() override;
private:
    virtual void onArgument(const ResultNode & arg, Int64ResultNode & result) const = 0;
    void onArgument(const ResultNode & arg, ResultNode & result) const override;
};

}
}

