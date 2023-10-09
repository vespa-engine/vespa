// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "resultnode.h"

namespace search::expression {

class SingleResultNode : public ResultNode
{
public:
    virtual ~SingleResultNode() { }
    DECLARE_ABSTRACT_RESULTNODE(SingleResultNode);
    using CP = vespalib::IdentifiablePtr<SingleResultNode>;
    using UP = std::unique_ptr<SingleResultNode>;
    virtual SingleResultNode *clone() const override = 0;

    virtual void min(const ResultNode & b) = 0;
    virtual void max(const ResultNode & b) = 0;
    virtual void add(const ResultNode & b) = 0;

    virtual void setMin() = 0;
    virtual void setMax() = 0;
    virtual size_t onGetRawByteSize() const = 0;
    size_t getRawByteSize() const override { return onGetRawByteSize(); }
};

}
