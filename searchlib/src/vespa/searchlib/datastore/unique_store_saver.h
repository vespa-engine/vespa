// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"

namespace search {
namespace datastore {

/**
 * Saver for related UniqueStore class.
 *
 * Contains utility methods for traversing all unique values (as
 * EntryRef value) and mapping from EntryRef value to enum value.
 */
template <typename EntryT, typename RefT>
class UniqueStoreSaver {
    using UniqueStoreType = UniqueStore<EntryT, RefT>;
    using Dictionary = typename UniqueStoreType::Dictionary;
    using ConstIterator = typename Dictionary::ConstIterator;
    using EntryType = EntryT;
    using RefType = RefT;

    ConstIterator _itr;
    const DataStoreBase &_store;
    std::vector<std::vector<uint32_t>> _enumValues;
public:
    UniqueStoreSaver(const Dictionary &dict, const DataStoreBase &store);
    ~UniqueStoreSaver();
    void enumerateValues();

    template <typename Function>
    void
    foreach_key(Function &&func) const
    {
        _itr.foreach_key(func);
    }

    uint32_t mapEntryRefToEnumValue(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            assert(iRef.offset() < _enumValues[iRef.bufferId()].size());
            uint32_t enumValue = _enumValues[iRef.bufferId()][iRef.offset()];
            assert(enumValue != 0);
            return enumValue;
        } else {
            return 0u;
        }
    }
};

}
}
