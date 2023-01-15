// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_comparator.h"
#include "unique_store_entry.h"
#include "datastore.h"
#include <vespa/vespalib/stllike/hash_fun.h>
#include <cmath>

namespace vespalib::datastore {

/**
 * Helper class for comparing elements in unique store.
 */
template <typename EntryT>
class UniqueStoreComparatorHelper {
public:
    static bool less(const EntryT& lhs, const EntryT& rhs) {
        return lhs < rhs;
    }
    static bool equal(const EntryT& lhs, const EntryT& rhs) {
        return lhs == rhs;
    }
    static size_t hash(const EntryT& rhs) {
        vespalib::hash<EntryT> hasher;
        return hasher(rhs);
    }
};

/**
 * Helper class for comparing floating point elements in unique store with
 * special handling of NAN.
 */
template <typename EntryT>
class UniqueStoreFloatingPointComparatorHelper
{
public:
    static bool less(EntryT lhs, const EntryT rhs) {
        if (std::isnan(lhs)) {
            return !std::isnan(rhs);
        } else if (std::isnan(rhs)) {
            return false;
        } else {
            return (lhs < rhs);
        }
    }
    static bool equal(EntryT lhs, const EntryT rhs) {
        if (std::isnan(lhs)) {
            return std::isnan(rhs);
        } else if (std::isnan(rhs)) {
            return false;
        } else {
            return (lhs == rhs);
        }
    }
    static size_t hash(EntryT rhs) {
        if (std::isnan(rhs)) {
            return 0;
        } else {
            union U { EntryT f; std::conditional_t<std::is_same_v<double, EntryT>, uint64_t, uint32_t> i; };
            U t;
            t.f = rhs;
            return t.i;
        }
    }
};

/**
 * Specialized helper class for comparing float elements in unique store with
 * special handling of NAN.
 */
template <>
class UniqueStoreComparatorHelper<float> : public UniqueStoreFloatingPointComparatorHelper<float> {
};

/**
 * Specialized helper class for comparing double elements in unique store with
 * special handling of NAN.
 */
template <>
class UniqueStoreComparatorHelper<double> : public UniqueStoreFloatingPointComparatorHelper<double> {
};

/**
 * Compare two entries based on entry refs.
 *
 * Valid entry ref is mapped to an entry in a data store.
 * Invalid entry ref is mapped to a temporary entry owned by comparator instance.
 */
template <typename EntryT, typename RefT>
class UniqueStoreComparator : public EntryComparator {
protected:
    using EntryType = EntryT;
    using WrappedEntryType = UniqueStoreEntry<EntryType>;
    using RefType = RefT;
    using DataStoreType = DataStoreT<RefT>;
    const DataStoreType &_store;
    const EntryType _fallback_value;

    inline const EntryType &get(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            return _store.template getEntry<WrappedEntryType>(iRef)->value();
        } else {
            return _fallback_value;
        }
    }

public:
    UniqueStoreComparator(const DataStoreType &store, const EntryType &fallback_value)
        : _store(store),
          _fallback_value(fallback_value)
    {
    }

    UniqueStoreComparator(const DataStoreType &store)
        : _store(store),
          _fallback_value()
    {
    }

    bool less(const EntryRef lhs, const EntryRef rhs) const override {
        const EntryType &lhsValue = get(lhs);
        const EntryType &rhsValue = get(rhs);
        return UniqueStoreComparatorHelper<EntryT>::less(lhsValue, rhsValue);
    }
    bool equal(const EntryRef lhs, const EntryRef rhs) const override {
        const EntryType &lhsValue = get(lhs);
        const EntryType &rhsValue = get(rhs);
        return UniqueStoreComparatorHelper<EntryT>::equal(lhsValue, rhsValue);
    }
    size_t hash(const EntryRef rhs) const override {
        const EntryType &rhsValue = get(rhs);
        return UniqueStoreComparatorHelper<EntryT>::hash(rhsValue);
    }
};

}
