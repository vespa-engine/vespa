// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singleresultnode.h"

namespace search::expression {

class RawResultNode : public SingleResultNode {
public:
    DECLARE_EXPRESSIONNODE(RawResultNode);
    DECLARE_NBO_SERIALIZE;
    DECLARE_RESULTNODE_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    RawResultNode() : _value(1) { setBuffer("", 0); }
    RawResultNode(const void* buf, size_t sz) { setBuffer(buf, sz); }
    int onCmp(const Identifiable& b) const override;
    size_t hash() const override;
    void set(const ResultNode& rhs) override;
    void setBuffer(const void* buf, size_t sz);
    ConstBufferRef get() const { return ConstBufferRef(&_value[0], _value.size()); }
    void min(const ResultNode& b) override;
    void max(const ResultNode& b) override;
    void add(const ResultNode& b) override;
    void negate() override;
    const BucketResultNode& getNullBucket() const override;

private:
    using V = std::vector<uint8_t>;
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index) const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;

    std::string_view friendly_type_name() const noexcept override { return "string"; }

    V _value;
};

} // namespace search::expression
