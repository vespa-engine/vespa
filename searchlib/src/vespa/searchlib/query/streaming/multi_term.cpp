// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_term.h"
#include <vespa/searchlib/query/tree/term_vector.h>

using search::fef::ITermData;
using search::fef::MatchData;
using search::query::TermVector;

namespace search::streaming {

MultiTerm::MultiTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms)
    : QueryTerm(std::move(result_base), "", std::move(index), Type::WORD, Normalizing::NONE),
      _terms()
{
    _terms.reserve(num_terms);
}

MultiTerm::MultiTerm(std::unique_ptr<QueryNodeResultBase> result_base, string index,
                     std::unique_ptr<TermVector> terms, Normalizing normalizing)
    : MultiTerm(std::move(result_base), std::move(index), terms->size())
{
    auto num_terms = terms->size();
    for (uint32_t i = 0; i < num_terms; ++i) {
        add_term(std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), terms->getAsString(i).first, "", Type::WORD, normalizing));
    }
}

MultiTerm::~MultiTerm() = default;

void
MultiTerm::add_term(std::unique_ptr<QueryTerm> term)
{
    _terms.emplace_back(std::move(term));
}

void
MultiTerm::reset()
{
    for (auto& term : _terms) {
        term->reset();
    }
}

bool
MultiTerm::evaluate() const
{
    for (const auto& term : _terms) {
        if (term->evaluate()) return true;
    }
    return false;
}

MultiTerm*
MultiTerm::as_multi_term() noexcept
{
    return this;
}

const MultiTerm*
MultiTerm::as_multi_term() const noexcept
{
    return this;
}

bool
MultiTerm::multi_index_terms() const noexcept
{
    return false;
}

}
