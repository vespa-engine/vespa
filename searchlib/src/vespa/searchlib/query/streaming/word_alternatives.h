// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multi_term.h"

namespace search::streaming {

class QueryVisitor;


class QueryVisitor;

/**
 * A word alternatives query term for streaming search.
 * Represents a set of alternative word forms that should match.
 */
class WordAlternatives : public MultiTerm {
public:
    WordAlternatives(std::unique_ptr<QueryNodeResultBase> result_base, const string& index,
                     std::unique_ptr<query::TermVector> terms, Normalizing normalize_mode);
    ~WordAlternatives() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data,
                           const fef::IIndexEnvironment& index_env, search::common::ElementIds element_ids) override;
    void accept(QueryVisitor &visitor);
};

}
