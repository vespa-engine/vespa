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
    MultiTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string & index, Type type, uint32_t num_terms);
    MultiTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string & index, Type type, std::unique_ptr<query::TermVector> terms);
    ~MultiTerm() override;
    void add_term(std::unique_ptr<QueryTerm> term);
    MultiTerm* as_multi_term() noexcept override;
    void reset() override;
    bool evaluate() const override;
    const HitList& evaluateHits(HitList& hl) const override;
    virtual void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data) = 0;
    const std::vector<std::unique_ptr<QueryTerm>>& get_terms() const noexcept { return _terms; }
};

}
