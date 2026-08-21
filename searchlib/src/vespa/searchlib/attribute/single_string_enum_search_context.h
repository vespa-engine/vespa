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
template <typename Matcher>
class SingleStringEnumSearchContextT : public SingleEnumSearchContext<const char*, StringSearchContextT<Matcher>> {
public:
    SingleStringEnumSearchContextT(Matcher&& matcher, const AttributeVector& toBeSearched,
                                   typename SingleStringEnumSearchContextT::EnumIndices enum_indices,
                                   const EnumStoreT<const char*>&                       enum_store);
    SingleStringEnumSearchContextT(SingleStringEnumSearchContextT&&) noexcept;
    ~SingleStringEnumSearchContextT() override;
};

} // namespace search::attribute
