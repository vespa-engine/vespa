// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"

namespace search::datastore {

/**
 * Saver for related UniqueStore class.
 *
 * Contains utility methods for traversing all unique values (as
 * EntryRef value) and mapping from EntryRef value to enum value.
 */
template <typename EntryT, typename RefT>
class UniqueStoreSaver {
    using EntryType = EntryT;
    using RefType = RefT;

    const UniqueStoreDictionaryBase &_dict;
    EntryRef _root;
    const DataStoreBase &_store;
    std::vector<std::vector<uint32_t>> _enumValues;
    uint32_t _next_enum_val;
public:
    UniqueStoreSaver(const UniqueStoreDictionaryBase &dict, const DataStoreBase &store);
    ~UniqueStoreSaver();
    void enumerateValue(EntryRef ref);
    void enumerateValues();

    template <typename Function>
    void
    foreach_key(Function &&func) const
    {
        _dict.foreach_key(_root, func);
    }

    uint32_t mapEntryRefToEnumValue(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            assert(iRef.unscaled_offset() < _enumValues[iRef.bufferId()].size());
            uint32_t enumValue = _enumValues[iRef.bufferId()][iRef.unscaled_offset()];
            assert(enumValue != 0);
            return enumValue;
        } else {
            return 0u;
        }
    }
};

}
