// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"

namespace search::fef {

class ITermData;
class MatchData;

}

namespace search::query { class TermVector; }

namespace search::streaming {

/*
 * Base class for query term nodes that are considered leaf nodes by
 * the ranking framework but still have multiple terms used for
 * search.
 */
class MultiTerm : public QueryTerm {
protected:
    std::vector<std::unique_ptr<QueryTerm>> _terms;
public:
    MultiTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms);
    MultiTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index,
              std::unique_ptr<query::TermVector> terms, Normalizing normalizing);
    ~MultiTerm() override;
    void add_term(std::unique_ptr<QueryTerm> term);
    MultiTerm* as_multi_term() noexcept override;
    const MultiTerm* as_multi_term() const noexcept override;
    /*
     * Terms below search in different indexes when multi_index_terms() returns true.
     */
    virtual bool multi_index_terms() const noexcept;
    void reset() override;
    bool evaluate() const override;
    const std::vector<std::unique_ptr<QueryTerm>>& get_terms() const noexcept { return _terms; }
};

}
