// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketresultnode.h"

namespace search {
namespace expression {

class IntegerBucketResultNode : public BucketResultNode
{
private:
    int64_t _from;
    int64_t _to;
    static IntegerBucketResultNode _nullResult;

    virtual size_t onGetRawByteSize() const { return sizeof(_from) + sizeof(_to); }
    virtual void create(void * buf)  const  { (void) buf; }
    virtual void destroy(void * buf) const  { (void) buf; }
    virtual void encode(void * buf) const {
        int64_t * v(static_cast<int64_t *>(buf));
        v[0] = _from;
        v[1] = _to;
    }
    virtual size_t hash(const void * buf) const { return static_cast<const int64_t *>(buf)[0]; }
    virtual void decode(const void * buf) {
        const int64_t * v(static_cast<const int64_t *>(buf));
        _from = v[0];
        _to = v[1];
    }
#if 0
#endif
public:
    DECLARE_EXPRESSIONNODE(IntegerBucketResultNode);
    DECLARE_NBO_SERIALIZE;
    IntegerBucketResultNode() : _from(0), _to(0) {}
    IntegerBucketResultNode(int64_t from, int64_t to) : _from(from), _to(to) {}
    virtual size_t hash() const;
    virtual int onCmp(const Identifiable & b) const;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    int contains(const IntegerBucketResultNode & b) const;
    int contains(int64_t v) const { return (v < _from) ? 1 : (v >= _to) ? -1 : 0; }
    IntegerBucketResultNode &setRange(int64_t from, int64_t to) {
        _from = from;
        _to = to;
        return *this;
    }
    virtual const IntegerBucketResultNode& getNullBucket() const override { return getNull(); }
    static const IntegerBucketResultNode & getNull() { return _nullResult; }
};

}
}

