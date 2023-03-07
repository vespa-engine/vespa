// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "integerresultnode.h"

namespace search::expression {

class EnumResultNode : public IntegerResultNodeT<int64_t>
{
private:
    using Base = IntegerResultNodeT<int64_t>;
public:
    DECLARE_RESULTNODE(EnumResultNode);
    EnumResultNode(int64_t v=0) : Base(v) { }
    void set(const ResultNode & rhs) override { setValue(rhs.getEnum()); }
private:
    int64_t onGetEnum(size_t index) const override { (void) index; return getValue(); }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
};

}
