// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_comparator.h"
#include "unique_store_string_allocator.h"
#include <vespa/vespalib/stllike/hash_fun.h>

namespace vespalib::datastore {

/**
 * Compare two strings based on entry refs.
 *
 * Valid entry ref is mapped to a string in a data store.
 * Invalid entry ref is mapped to a temporary string pointed to by comparator instance.
 */
template <typename RefT>
class UniqueStoreStringComparator : public EntryComparator {
protected:
    using RefType = RefT;
    using WrappedExternalEntryType = UniqueStoreEntry<std::string>;
    using DataStoreType = DataStoreT<RefT>;
    const DataStoreType &_store;
    const char *_lookup_value;

    const char *get(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            const auto &meta = _store.getBufferMeta(iRef.bufferId());
            auto type_id = meta.getTypeId();
            if (type_id != 0) {
                return reinterpret_cast<const UniqueStoreSmallStringEntry *>(_store.template getEntryArray<char>(iRef, meta.get_array_size()))->value();
            } else {
                return _store.template getEntry<WrappedExternalEntryType>(iRef)->value().c_str();
            }
        } else {
            return _lookup_value;
        }
    }
    UniqueStoreStringComparator(const DataStoreType &store, const char *lookup_value)
        : _store(store),
          _lookup_value(lookup_value)
    {
    }
public:
    UniqueStoreStringComparator(const DataStoreType &store)
        : _store(store),
          _lookup_value(nullptr)
    {
    }
    bool less(const EntryRef lhs, const EntryRef rhs) const override {
        const char *lhs_value = get(lhs);
        const char *rhs_value = get(rhs);
        return (strcmp(lhs_value, rhs_value) < 0);
    }
    bool equal(const EntryRef lhs, const EntryRef rhs) const override {
        const char *lhs_value = get(lhs);
        const char *rhs_value = get(rhs);
        return (strcmp(lhs_value, rhs_value) == 0);
    }
    size_t hash(const EntryRef rhs) const override {
        const char *rhs_value = get(rhs);
        vespalib::hash<const char *> hasher;
        return hasher(rhs_value);
    }
    UniqueStoreStringComparator<RefT> make_for_lookup(const char* lookup_value) const {
        return UniqueStoreStringComparator<RefT>(_store, lookup_value);
    }
};

}
