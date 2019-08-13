// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"

namespace search::datastore {

/**
 * Builder for related UniqueStore class.
 *
 * Contains utility method for adding new unique values and mapping
 * from enum value to EntryRef value.  New unique values must be added
 * in sorted order.
 */
template <typename EntryT, typename RefT>
class UniqueStoreBuilder {
    using UniqueStoreType = UniqueStore<EntryT, RefT>;
    using EntryType = EntryT;

    UniqueStoreType &_store;
    UniqueStoreDictionaryBase &_dict;
    std::vector<EntryRef> _refs;
    std::vector<uint32_t> _refCounts;
public:
    UniqueStoreBuilder(UniqueStoreType &store,
                       UniqueStoreDictionaryBase &dict, uint32_t uniqueValuesHint);
    ~UniqueStoreBuilder();
    void setupRefCounts();
    void makeDictionary();
    void add(const EntryType &value) {
        EntryRef newRef = _store.allocate(value);
        _refs.push_back(newRef);
    }
    EntryRef mapEnumValueToEntryRef(uint32_t enumValue) {
        assert(enumValue < _refs.size());
        ++_refCounts[enumValue];
        return _refs[enumValue];
    }
};

}
