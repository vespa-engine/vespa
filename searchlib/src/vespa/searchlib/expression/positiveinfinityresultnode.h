// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search {
namespace expression {

class PositiveInfinityResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(PositiveInfinityResultNode);
    virtual size_t hash() const;
    virtual int onCmp(const Identifiable & b) const;
    virtual void set(const ResultNode & rhs);
    virtual void min(const ResultNode & b);
    virtual void max(const ResultNode & b);
    virtual void add(const ResultNode & b);
private:
    virtual void setMin();
    virtual void setMax();
    virtual int64_t onGetInteger(size_t index) const;
    virtual double onGetFloat(size_t index)    const;
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const;
    virtual size_t onGetRawByteSize() const { return 0; }
};

}
}

