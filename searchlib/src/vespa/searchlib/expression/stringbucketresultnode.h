// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketresultnode.h"
#include "stringresultnode.h"

namespace search {
namespace expression {

class StringBucketResultNode : public BucketResultNode
{
private:
    ResultNode::CP _from;
    ResultNode::CP _to;
    static StringBucketResultNode _nullResult;
    size_t onGetRawByteSize() const override { return sizeof(_from) + sizeof(_to); }
public:
    struct GetValue {
        BufferRef _tmp;
        ConstBufferRef operator () (const ResultNode & r) { return r.getString(_tmp); }
    };

    DECLARE_EXPRESSIONNODE(StringBucketResultNode);
    DECLARE_NBO_SERIALIZE;
    StringBucketResultNode();
    StringBucketResultNode(const StringBucketResultNode&);
    StringBucketResultNode(StringBucketResultNode&&) noexcept = default;
    StringBucketResultNode(vespalib::stringref from, vespalib::stringref to);
    StringBucketResultNode(ResultNode::UP from, ResultNode::UP to) : _from(from.release()), _to(to.release()) {}
    ~StringBucketResultNode();
    StringBucketResultNode& operator=(const StringBucketResultNode&);
    StringBucketResultNode& operator=(StringBucketResultNode&&);
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    int contains(const StringBucketResultNode & b) const;
    int contains(const ConstBufferRef & v) const { return contains(v.c_str()); }
    int contains(const char * v) const;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    StringBucketResultNode &setRange(vespalib::stringref from, vespalib::stringref to) {
        _from.reset(new StringResultNode(from));
        _to.reset(new StringResultNode(to));
        return *this;
    }
    const StringBucketResultNode& getNullBucket() const override { return getNull(); }
    static const StringBucketResultNode & getNull() { return _nullResult; }
};

}
}

