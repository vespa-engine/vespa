// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketresultnode.h"

namespace search {
namespace expression {

class FloatBucketResultNode : public BucketResultNode
{
private:
    double _from;
    double _to;
    static FloatBucketResultNode _nullResult;
    size_t onGetRawByteSize() const override { return sizeof(_from) + sizeof(_to); }
    void create(void * buf)  const override  { (void) buf; }
    void destroy(void * buf) const override  { (void) buf; }
    void encode(void * buf) const override {
        double * v(static_cast<double *>(buf));
        v[0] = _from;
        v[1] = _to;
    }
    size_t hash(const void * buf) const override { return static_cast<const size_t *>(buf)[0]; }
    void decode(const void * buf) override {
        const double * v(static_cast<const double *>(buf));
        _from = v[0];
        _to = v[1];
    }
public:
    struct GetValue {
        double operator () (const ResultNode & r) { return r.getFloat(); }
    };

    DECLARE_EXPRESSIONNODE(FloatBucketResultNode);
    DECLARE_NBO_SERIALIZE;
    FloatBucketResultNode() noexcept : _from(0.0), _to(0.0) {}
    FloatBucketResultNode(double from, double to) : _from(from), _to(to) {}
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    int contains(const FloatBucketResultNode & b) const;
    int contains(double v) const { return (v < _from) ? 1 : (v >= _to) ? -1 : 0; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    FloatBucketResultNode &setRange(double from, double to) {
        _from = from;
        _to = to;
        return *this;
    }
    const FloatBucketResultNode& getNullBucket() const override { return getNull(); }
    static const FloatBucketResultNode & getNull() { return _nullResult; }
};

}
}

