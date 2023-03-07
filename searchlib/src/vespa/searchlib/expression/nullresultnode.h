// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singleresultnode.h"

namespace search::expression {

class NullResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(NullResultNode);
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    void set(const ResultNode & rhs) override;
    void min(const ResultNode & b) override;
    void max(const ResultNode & b) override;
    void add(const ResultNode & b) override;
private:
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index)    const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
    size_t onGetRawByteSize() const override { return 0; }
    void create(void * buf)  const override { (void) buf; }
    void destroy(void * buf) const override { (void) buf;}

    void decode(const void * buf) override { (void) buf; }
    void encode(void * buf) const override { (void) buf; }
    void swap(void * buf) override { (void) buf; }
};

}
