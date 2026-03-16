// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketresultnode.h"

namespace search::expression {

class IntegerBucketResultNode : public BucketResultNode
{
private:
    int64_t _from;
    int64_t _to;
    static IntegerBucketResultNode _nullResult;

    std::string_view friendly_type_name() const noexcept override { return "integer_bucket"; }

public:
    DECLARE_EXPRESSIONNODE(IntegerBucketResultNode);
    DECLARE_NBO_SERIALIZE;
    IntegerBucketResultNode() noexcept : _from(0), _to(0) {}
    IntegerBucketResultNode(int64_t from, int64_t to) : _from(from), _to(to) {}
    size_t hash() const override;
    int onCmp(const Identifiable & b) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    int contains(const IntegerBucketResultNode & b) const;
    int contains(int64_t v) const { return (v < _from) ? 1 : (v >= _to) ? -1 : 0; }
    IntegerBucketResultNode &setRange(int64_t from, int64_t to) {
        _from = from;
        _to = to;
        return *this;
    }
    const IntegerBucketResultNode& getNullBucket() const override { return getNull(); }
    static const IntegerBucketResultNode & getNull() { return _nullResult; }
};

}
