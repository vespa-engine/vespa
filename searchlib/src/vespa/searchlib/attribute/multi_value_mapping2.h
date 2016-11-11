// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/datastore/array_store.h>

namespace search {
namespace attribute {

template <typename EntryT, typename RefT = datastore::EntryRefT<17> >
class MultiValueMapping2
{
    using EntryRef = datastore::EntryRef;
    using IndexVector = RcuVectorBase<EntryRef>;
    using ArrayStore = datastore::ArrayStore<EntryT, RefT>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;

    ArrayStore _store;
    IndexVector _indices;
public:
    MultiValueMapping2(uint32_t maxSmallArraySize);
    ~MultiValueMapping2();
    ConstArrayRef get(uint32_t docId) const { return _store.get(_indices[docId]); }
    void set(uint32_t docId, ConstArrayRef values);

    // replace is generally unsafe and should only be used when
    // compacting enum store (replacing old enum index with updated enum index)
    void replace(uint32_t docId, ConstArrayRef values);

    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _store.trimHoldLists(firstUsed); }
};

} // namespace search::attribute
} // namespace search
