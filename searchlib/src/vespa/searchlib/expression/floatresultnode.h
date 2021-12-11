// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericresultnode.h"
#include <vespa/vespalib/util/sort.h>

namespace search {
namespace expression {

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
    int cmpMem(const void * a, const void *b) const override {
        const double & ai(*static_cast<const double *>(a));
        const double & bi(*static_cast<const double *>(b));
        return ai < bi ? -1 : ai == bi ? 0 : 1;
    }
    void create(void * buf)  const override { (void) buf; }
    void destroy(void * buf) const override { (void) buf; }
    void decode(const void * buf) override { _value = *static_cast<const double *>(buf); }
    void encode(void * buf) const override { *static_cast<double *>(buf) = _value; }
    void swap(void * buf) override { std::swap(*static_cast<double *>(buf), _value); }
    size_t hash(const void * buf) const override { size_t tmpHash(0); memcpy(&tmpHash, buf, sizeof(tmpHash)); return tmpHash; }
    uint64_t  radixAsc(const void * buf) const override { return vespalib::convertForSort<double,  true>::convert(*static_cast<const double *>(buf)); }
     uint64_t radixDesc(const void * buf) const override { return vespalib::convertForSort<double, false>::convert(*static_cast<const double *>(buf)); }

    size_t onGetRawByteSize() const override { return sizeof(_value); }
    bool isNan() const;
    void setMin() override;
    void setMax() override;
    int64_t onGetInteger(size_t index) const override;
    double onGetFloat(size_t index)    const override;
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override;
    double _value;
};

}
}

