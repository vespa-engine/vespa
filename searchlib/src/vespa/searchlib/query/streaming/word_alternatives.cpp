// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "word_alternatives.h"
#include "query_visitor.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/query/tree/term_vector.h>

namespace search::streaming {

WordAlternatives::WordAlternatives(std::unique_ptr<QueryNodeResultBase> result_base, const string& index,
                                   std::unique_ptr<query::TermVector> terms, Normalizing normalize_mode)
    : MultiTerm(std::move(result_base), index, std::move(terms), normalize_mode)
{
}

WordAlternatives::~WordAlternatives() = default;

void
WordAlternatives::get_element_ids(std::vector<uint32_t>& element_ids)
{
    for (const auto& term : _terms) {
        term->get_element_ids(element_ids);
    }
}

void
WordAlternatives::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data,
                                    const fef::IIndexEnvironment& index_env, search::common::ElementIds element_ids)
{
    for (const auto& term : _terms) {
        term->unpack_match_data(docid, td, match_data, index_env, element_ids);
    }
}

void
WordAlternatives::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
