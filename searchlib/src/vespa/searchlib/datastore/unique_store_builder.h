// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"

namespace search {
namespace datastore {

/**
 * Builder for related UniqueStore class.
 *
 * Contains utility method for adding new unique values and mapping from
 * enum value to EntryRef value.
 */
template <typename EntryT, typename RefT>
class UniqueStoreBuilder {
    using UniqueStoreType = UniqueStore<EntryT, RefT>;
    using DataStoreType = typename UniqueStoreType::DataStoreType;
    using Dictionary = typename UniqueStoreType::Dictionary;
    using EntryType = EntryT;
    using RefType = RefT;

    DataStoreType &_store;
    uint32_t _typeId;
    Dictionary &_dict;
    std::vector<EntryRef> _refs;
    std::vector<uint32_t> _refCounts;
public:
    UniqueStoreBuilder(DataStoreType &store, uint32_t typeId,
                       Dictionary &dict, uint32_t uniqueValuesHint);
    ~UniqueStoreBuilder();
    void setupRefCounts();
    void makeDictionary();
    void add(const EntryType &value) {
        EntryRef newRef = _store.template allocator<EntryType>(_typeId).alloc(value).ref;
        _refs.push_back(newRef);
    }
    EntryRef mapEnumValueToEntryRef(uint32_t enumValue) {
        assert(enumValue < _refs.size());
        ++_refCounts[enumValue];
        return _refs[enumValue];
    }
};

}
}
