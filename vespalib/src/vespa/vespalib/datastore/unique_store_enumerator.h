// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_dictionary_base.h"

namespace search::datastore {

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
    using EnumValues = std::vector<std::vector<uint32_t>>;

private:
    const UniqueStoreDictionaryBase &_dict;
    EntryRef _frozen_root;
    const DataStoreBase &_store;
    EnumValues _enumValues;
    uint32_t _next_enum_val;
public:
    UniqueStoreEnumerator(const UniqueStoreDictionaryBase &dict, const DataStoreBase &store);
    ~UniqueStoreEnumerator();
    EntryRef get_frozen_root() const { return _frozen_root; }
    void enumerateValue(EntryRef ref);
    void enumerateValues();
    void clear();

    template <typename Function>
    void
    foreach_key(Function &&func) const
    {
        _dict.foreach_key(_frozen_root, func);
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

    uint32_t map_entry_ref_to_enum_value_or_zero(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            if (iRef.unscaled_offset() < _enumValues[iRef.bufferId()].size()) {
                return  _enumValues[iRef.bufferId()][iRef.unscaled_offset()];
            } else {
                return 0u;
            }
        } else {
            return 0u;
        }
    }
};

}
