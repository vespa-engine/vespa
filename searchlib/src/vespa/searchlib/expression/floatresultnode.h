// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericresultnode.h"
#include <vespa/vespalib/util/sort.h>

namespace search ::expression {

class FloatResultNode final : public NumericResultNode
{
public:
    DECLARE_EXPRESSIONNODE(FloatResultNode);
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    FloatResultNode(double v=0) noexcept : _value(v) { }
    size_t hash() const override { size_t tmpHash(0); memcpy(&tmpHash, &_value, sizeof(tmpHash)); return tmpHash; }
    int onCmp(const Identifiable & b) const override;
    void add(const ResultNode & b) override;
    void negate() override;
    void multiply(const ResultNode & b) override;
    void divide(const ResultNode & b) override;
    void modulo(const ResultNode & b) override;
    void min(const ResultNode & b) override;
    void max(const ResultNode & b) override;
    void set(const ResultNode & rhs) override;
    double get() const { return _value; }
    void set(double value) { _value = value; }
    const BucketResultNode& getNullBucket() const override;

private:
    bool isNan() const;
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index)    const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;

    std::string_view friendly_type_name() const noexcept override { return "double"; }

    double _value;
};

}
