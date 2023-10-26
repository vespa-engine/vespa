// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_enum_search_context.h"
#include "string_search_context.h"
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search::attribute {

/*
 * SingleStringEnumSearchContext handles the creation of search iterators for
 * a query term on a single value string enumerated attribute vector.
 */
class SingleStringEnumSearchContext : public SingleEnumSearchContext<const char*, StringSearchContext>
{
public:
    SingleStringEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased,
                                  vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm,
                                  const AttributeVector& toBeSearched,
                                  EnumIndices enum_indices,
                                  const EnumStoreT<const char*>& enum_store);
    SingleStringEnumSearchContext(SingleStringEnumSearchContext&&) noexcept;
    ~SingleStringEnumSearchContext() override;
};

}
