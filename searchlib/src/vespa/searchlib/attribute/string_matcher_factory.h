// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_cased_matcher.h"
#include "string_fuzzy_matcher.h"
#include "string_range_matcher.h"
#include "string_regex_matcher.h"
#include "string_uncased_matcher.h"

#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

#include <memory>

namespace search::attribute {

/**
 * Factory for creating string matchers.
 **/
class StringMatcherFactory {
public:
    /**
     * Creates the string matcher handling the type of the query term (string range, regex, fuzzy,
     * cased or uncased word/prefix) and passes it to func. All invocations of func must return the same type.
     **/
    template <typename Func>
    static auto create_and_apply(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                 vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm, Func&& func) {
        if (query_term && query_term->get_string_range_spec()) {
            return func(StringRangeMatcher(std::move(query_term), cased));
        } else if (query_term->isRegex()) {
            return func(StringRegexMatcher(std::move(query_term), cased));
        } else if (query_term->isFuzzy()) {
            return func(StringFuzzyMatcher(std::move(query_term), cased, fuzzy_matching_algorithm));
        } else if (cased) {
            return func(StringCasedMatcher(std::move(query_term)));
        } else {
            return func(StringUncasedMatcher(std::move(query_term)));
        }
    }
};

} // namespace search::attribute
