// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    size_t onGetRawByteSize() const override { return sizeof(_from) + sizeof(_to); }
public:
    struct GetValue {
        BufferRef _tmp;
        ConstBufferRef operator () (const ResultNode & r) { return r.getString(_tmp); }
    };

    DECLARE_EXPRESSIONNODE(RawBucketResultNode);
    DECLARE_NBO_SERIALIZE;
    RawBucketResultNode();
    RawBucketResultNode(const RawBucketResultNode&);
    RawBucketResultNode(RawBucketResultNode&&) noexcept = default;
    RawBucketResultNode(ResultNode::UP from, ResultNode::UP to) : _from(from.release()), _to(to.release()) {}
    ~RawBucketResultNode();
    RawBucketResultNode& operator=(const RawBucketResultNode&);
    RawBucketResultNode& operator=(RawBucketResultNode&&) noexcept;
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    int contains(const RawBucketResultNode & b) const;
    int contains(const ConstBufferRef & v) const;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const RawBucketResultNode& getNullBucket() const override { return getNull(); }
    static const RawBucketResultNode & getNull() { return _nullResult; }
};

}
}

