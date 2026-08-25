// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/integerresultnode.h>

namespace search::aggregation {

/**
 * Counting aggregator that counts every single-value hit as 1, and every
 * multi-value hit as its size.
 */
class CountAggregationResult : public AggregationResult {
    expression::Int64ResultNode _count;

public:
    DECLARE_AGGREGATIONRESULT(CountAggregationResult);
    CountAggregationResult(uint64_t count = 0);
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    [[nodiscard]] uint64_t getCount() const { return _count.get(); }
    CountAggregationResult& setCount(uint64_t c) {
        _count = c;
        return *this;
    }

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return _count; }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
};

} // namespace search::aggregation
