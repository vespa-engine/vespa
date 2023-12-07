// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

/*
 * Representation of YQL in operator for streaming search.
 */
class InTerm : public MultiTerm {
public:
    InTerm(std::unique_ptr<QueryNodeResultBase> result_base, const string& index, std::unique_ptr<query::TermVector> terms);
    ~InTerm() override;
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data) override;
};

}
