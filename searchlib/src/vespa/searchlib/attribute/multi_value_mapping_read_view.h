// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.h>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.h>
#include <vespa/vespalib/util/address_space.h>

namespace search::attribute {

/**
 * Class for mapping from document id to an array of values as reader.
 */
template <typename ElemT, typename RefT = vespalib::datastore::EntryRefT<19> >
class MultiValueMappingReadView
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using Indices = vespalib::ConstArrayRef<AtomicEntryRef>;
    using ArrayStoreTypeMapper = vespalib::datastore::ArrayStoreDynamicTypeMapper<ElemT>;
    using ArrayStore = vespalib::datastore::ArrayStore<ElemT, RefT, ArrayStoreTypeMapper>;

    Indices           _indices;
    const ArrayStore* _store;
public:
    constexpr MultiValueMappingReadView()
        : _indices(),
          _store(nullptr)
    {
    }
    MultiValueMappingReadView(Indices indices, const ArrayStore* store)
        : _indices(indices),
          _store(store)
    {
    }
    vespalib::ConstArrayRef<ElemT> get(uint32_t doc_id) const { return _store->get(_indices[doc_id].load_acquire()); }
    bool valid() const noexcept { return _store != nullptr; }
    uint32_t get_committed_docid_limit() const noexcept { return _indices.size(); }
};

}
