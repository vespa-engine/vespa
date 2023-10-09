// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_string_enum_search_context.h"
#include "enumhintsearchcontext.h"
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search::attribute {

/*
 * SingleStringEnumHintSearchContext handles the creation of search iterators
 * for a query term on a single value string enumerated attribute vector using
 * dictionary information to eliminate searches for nonexisting words.
 */
class SingleStringEnumHintSearchContext : public SingleStringEnumSearchContext,
                                          public EnumHintSearchContext
{
public:
    SingleStringEnumHintSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased,
                                      vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm,
                                      const AttributeVector& toBeSearched,
                                      EnumIndices enum_indices,
                                      const EnumStoreT<const char*>& enum_store,
                                      uint64_t num_values);
    ~SingleStringEnumHintSearchContext() override;
};

}
