// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singleresultnode.h"

namespace search::expression {

class RawResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(RawResultNode);
    DECLARE_NBO_SERIALIZE;
    DECLARE_RESULTNODE_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    RawResultNode() : _value(1) { setBuffer("", 0); }
    RawResultNode(const void * buf, size_t sz) { setBuffer(buf, sz); }
    int onCmp(const Identifiable & b) const override;
    size_t hash() const override;
    void set(const ResultNode & rhs) override;
    void setBuffer(const void * buf, size_t sz);
    ConstBufferRef get() const { return ConstBufferRef(&_value[0], _value.size()); }
    void min(const ResultNode & b) override;
    void max(const ResultNode & b) override;
    void add(const ResultNode & b) override;
    void negate() override;
    const BucketResultNode& getNullBucket() const override;
private:
    using V = std::vector<uint8_t>;
    int cmpMem(const void * a, const void *b) const override {
        const V & ai(*static_cast<const V *>(a));
        const V & bi(*static_cast<const V *>(b));
        int result = memcmp(&ai[0], &bi[0], std::min(ai.size(), bi.size()));
        if (result == 0) {
            result = ai.size() < bi.size() ? -1 : ai.size() > bi.size() ? 1 : 0;
        }
        return result;
    }
    void decode(const void * buf) override { _value = *static_cast<const V *>(buf); }
    void encode(void * buf) const override { *static_cast<V *>(buf) = _value; }
    size_t hash(const void * buf) const override;

    size_t onGetRawByteSize() const override { return sizeof(_value); }
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index)    const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
    V _value;
};

}
