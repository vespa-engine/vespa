// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_unique_store_dictionary.h"
#include "i_unique_store_dictionary_read_snapshot.h"
#include <vespa/vespalib/stllike/allocator.h>
#include <cassert>

namespace vespalib::datastore {

class DataStoreBase;

/**
 * Enumerator for related UniqueStore class.
 *
 * Contains utility methods for traversing all unique values (as
 * EntryRef value) and mapping from EntryRef value to enum value.
 */
template <typename RefT>
class UniqueStoreEnumerator {
public:
    using RefType = RefT;

private:
    using UInt32Vector = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;
    using EnumValues = std::vector<UInt32Vector>;
    std::unique_ptr<IUniqueStoreDictionaryReadSnapshot> _dict_snapshot;
    const DataStoreBase &_store;
    EnumValues _enumValues;
    uint32_t _next_enum_val;

    void allocate_enum_values(DataStoreBase &store);
public:
    UniqueStoreEnumerator(const IUniqueStoreDictionary &dict, DataStoreBase &store, bool sort_unique_values);
    ~UniqueStoreEnumerator();
    void enumerateValue(EntryRef ref);
    void enumerateValues();
    void clear();

    template <typename Function>
    void
    foreach_key(Function &&func) const {
        _dict_snapshot->foreach_key(func);
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

    uint32_t map_entry_ref_to_enum_value_or_zero(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            if (iRef.offset() < _enumValues[iRef.bufferId()].size()) {
                return  _enumValues[iRef.bufferId()][iRef.offset()];
            } else {
                return 0u;
            }
        } else {
            return 0u;
        }
    }
};

}
