// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketresultnode.h"
#include "rawresultnode.h"

namespace search {
namespace expression {

class RawBucketResultNode : public BucketResultNode
{
private:
    ResultNode::CP _from;
    ResultNode::CP _to;
    static RawBucketResultNode _nullResult;
    virtual size_t onGetRawByteSize() const { return sizeof(_from) + sizeof(_to); }
public:
    struct GetValue {
        BufferRef _tmp;
        ConstBufferRef operator () (const ResultNode & r) { return r.getString(_tmp); }
    };

    DECLARE_EXPRESSIONNODE(RawBucketResultNode);
    DECLARE_NBO_SERIALIZE;
    RawBucketResultNode() : _from(new RawResultNode()), _to(new RawResultNode()) {}
    RawBucketResultNode(ResultNode::UP from, ResultNode::UP to) : _from(from.release()), _to(to.release()) {}
    virtual size_t hash() const;
    virtual int onCmp(const Identifiable & b) const;
    int contains(const RawBucketResultNode & b) const;
    int contains(const ConstBufferRef & v) const;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    static const RawBucketResultNode & getNull() { return _nullResult; }
};

}
}

