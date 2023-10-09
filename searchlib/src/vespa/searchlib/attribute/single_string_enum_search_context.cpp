// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_string_enum_search_context.h"
#include "single_enum_search_context.hpp"
#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

SingleStringEnumSearchContext::SingleStringEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased,
                                                             vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm,
                                                             const AttributeVector& toBeSearched,
                                                             EnumIndices enum_indices,
                                                             const EnumStoreT<const char*>& enum_store)
    : SingleEnumSearchContext<const char*, StringSearchContext>(StringMatcher(std::move(qTerm), cased, fuzzy_matching_algorithm),
                                                                toBeSearched, enum_indices, enum_store)
{
}

SingleStringEnumSearchContext::SingleStringEnumSearchContext(SingleStringEnumSearchContext&&) noexcept = default;

SingleStringEnumSearchContext::~SingleStringEnumSearchContext() = default;

}
