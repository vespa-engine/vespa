// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumhintsearchcontext.h"
#include "single_string_enum_search_context.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search::attribute {

/*
 * SingleStringEnumHintSearchContext handles the creation of search iterators
 * for a query term on a single value string enumerated attribute vector using
 * dictionary information to eliminate searches for nonexisting words.
 */
template <typename Matcher>
class SingleStringEnumHintSearchContextT : public SingleStringEnumSearchContextT<Matcher>,
                                           public EnumHintSearchContext {
public:
    SingleStringEnumHintSearchContextT(Matcher&& matcher, const AttributeVector& toBeSearched,
                                       typename SingleStringEnumHintSearchContextT::EnumIndices enum_indices,
                                       const EnumStoreT<const char*>& enum_store, uint64_t num_values);
    ~SingleStringEnumHintSearchContextT() override;
};

using SingleStringEnumHintSearchContext = SingleStringEnumHintSearchContextT<StringMatcher>;

} // namespace search::attribute
