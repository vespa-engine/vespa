// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"
#include <vespa/searchlib/attribute/dfa_fuzzy_matcher.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <memory>
#include <string_view>

namespace search::streaming {

/**
 * Query term that matches candidate field terms that are within a query-specified
 * maximum number of edits (add, delete or substitute a character), with case
 * sensitivity controlled by the provided Normalizing mode.
 *
 * Optionally, terms may be prefixed-locked, which enforces field terms to have a
 * particular prefix and where edits are only counted for the remaining term suffix.
 */
class FuzzyTerm : public QueryTerm {
    std::unique_ptr<attribute::DfaFuzzyMatcher> _dfa_matcher;
    std::unique_ptr<vespalib::FuzzyMatcher>     _fallback_matcher;
public:
    FuzzyTerm(std::unique_ptr<QueryNodeResultBase> result_base, stringref term,
              const string& index, Type type, Normalizing normalizing,
              uint8_t max_edits, uint32_t prefix_lock_length, bool prefix_match);
    ~FuzzyTerm() override;

    [[nodiscard]] FuzzyTerm* as_fuzzy_term() noexcept override { return this; }

    [[nodiscard]] bool is_match(std::string_view term) const;
};

}
