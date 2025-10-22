// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"

namespace search::streaming {

class QueryVisitor;


class QueryVisitor;

/**
 * A substring query term for streaming search.
 */
class SubstringTerm : public QueryTerm {
public:
    SubstringTerm(std::unique_ptr<QueryNodeResultBase> result_base, string_view term,
                  const string& index, Type type, Normalizing normalizing)
        : QueryTerm(std::move(result_base), term, index, type, normalizing)
    {}
    ~SubstringTerm() override;
    void accept(QueryVisitor &visitor);
};

}
