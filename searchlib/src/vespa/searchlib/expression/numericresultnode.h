// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singleresultnode.h"

namespace search::expression {

class NumericResultNode : public SingleResultNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(NumericResultNode);
    using CP = vespalib::IdentifiablePtr<NumericResultNode>;
    using UP = std::unique_ptr<NumericResultNode>;
    virtual NumericResultNode *clone() const override = 0;
    virtual void multiply(const ResultNode & b) = 0;
    virtual void divide(const ResultNode & b) = 0;
    virtual void modulo(const ResultNode & b) = 0;
};

}
