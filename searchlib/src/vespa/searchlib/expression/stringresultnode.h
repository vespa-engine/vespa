// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "singleresultnode.h"

namespace search::expression {

class StringResultNode : public SingleResultNode
{
public:
    DECLARE_EXPRESSIONNODE(StringResultNode);
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    StringResultNode(const char * v="") noexcept : _value(v) { }
    StringResultNode(std::string_view v) : _value(v) { }
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    void set(const ResultNode & rhs) override;
    StringResultNode & append(const ResultNode & rhs);
    StringResultNode & clear() { _value.clear(); return *this; }
    const std::string & get() const { return _value; }
    void set(std::string_view value) { _value = value; }
    void min(const ResultNode & b) override;
    void max(const ResultNode & b) override;
    void add(const ResultNode & b) override;
    void negate() override;
    const BucketResultNode& getNullBucket() const override;

private:
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index)    const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;

    std::string_view friendly_type_name() const noexcept override { return "string"; }

    std::string _value;
};

}
