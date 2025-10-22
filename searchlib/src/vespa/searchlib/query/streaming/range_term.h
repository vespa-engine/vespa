// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"

namespace search::streaming {

class QueryVisitor;


class QueryVisitor;

/**
 * A range query term for streaming search.
 */
class RangeTerm : public QueryTerm {
public:
    RangeTerm(Type type, string index, std::unique_ptr<NumericRangeSpec> range)
        : QueryTerm(type, std::move(index), std::move(range))
    {}
    ~RangeTerm() override;
    void accept(QueryVisitor &visitor);
};

}
