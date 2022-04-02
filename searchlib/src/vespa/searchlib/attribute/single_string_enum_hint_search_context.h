// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_string_enum_search_context.h"
#include "enumhintsearchcontext.h"

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
    SingleStringEnumHintSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased, const AttributeVector& toBeSearched, const vespalib::datastore::AtomicEntryRef* enum_indices, const EnumStoreT<const char*>& enum_store, uint32_t doc_id_limit, uint64_t num_values);
    ~SingleStringEnumHintSearchContext() override;
};

}
