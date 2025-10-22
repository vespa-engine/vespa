// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"

namespace search::streaming {

class QueryVisitor;


/**
 * A prefix query term for streaming search.
 */
class PrefixTerm : public QueryTerm {
public:
    PrefixTerm(std::unique_ptr<QueryNodeResultBase> result_base, string_view term,
               const string& index, Type type, Normalizing normalizing)
        : QueryTerm(std::move(result_base), term, index, type, normalizing)
    {}
    ~PrefixTerm() override;
    void accept(QueryVisitor &visitor);
};

}
