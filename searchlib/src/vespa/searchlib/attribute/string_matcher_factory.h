// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_matcher.h"
#include "string_range_matcher.h"

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
     * Creates the string matcher handling the type of the query term (string range or other)
     * and passes it to func. All invocations of func must return the same type.
     **/
    template <typename Func>
    static auto create_and_apply(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                 vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm, Func&& func) {
        if (query_term && query_term->get_string_range_spec()) {
            return func(StringRangeMatcher(std::move(query_term), cased));
        } else {
            return func(StringMatcher(std::move(query_term), cased, fuzzy_matching_algorithm));
        }
    }
};

} // namespace search::attribute
