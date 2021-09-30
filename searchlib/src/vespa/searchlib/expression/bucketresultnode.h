// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "resultnode.h"

namespace search::expression {

class BucketResultNode : public ResultNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(BucketResultNode);
    void set(const ResultNode & rhs) override { (void) rhs; }
protected:
    static const vespalib::string _fromField;
    static const vespalib::string _toField;
private:
    int64_t onGetInteger(size_t index) const override { (void) index; return 0; }
    double onGetFloat(size_t index)    const override { (void) index; return 0; }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override { (void) index; return buf; }
    size_t getRawByteSize() const override { return onGetRawByteSize(); }
    virtual size_t onGetRawByteSize() const = 0;
};

}
