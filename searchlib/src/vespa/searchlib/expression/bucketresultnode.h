// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "resultnode.h"

namespace search {
namespace expression {

class BucketResultNode : public ResultNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(BucketResultNode);
    virtual void set(const ResultNode & rhs) { (void) rhs; }
protected:
    static vespalib::FieldBase _fromField;
    static vespalib::FieldBase _toField;
private:
    virtual int64_t onGetInteger(size_t index) const { (void) index; return 0; }
    virtual double onGetFloat(size_t index)    const { (void) index; return 0; }
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const { (void) index; return buf; }
    virtual size_t getRawByteSize() const { return onGetRawByteSize(); }
    virtual size_t onGetRawByteSize() const = 0;
};

}
}

