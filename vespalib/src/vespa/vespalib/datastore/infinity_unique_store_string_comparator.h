// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unique_store_string_comparator.h"

namespace vespalib::datastore {

/**
 * Compare two strings based on entry refs.
 *
 * Valid entry ref is mapped to a string in a data store.
 * Invalid entry ref is mapped to negative infinity.
 */
template <typename RefT> class NegativeInfinityUniqueStoreStringComparator
    : public UniqueStoreStringComparator<RefT> {
public:
    NegativeInfinityUniqueStoreStringComparator(
        const typename UniqueStoreStringComparator<RefT>::DataStoreType& store) noexcept
        : UniqueStoreStringComparator<RefT>(store) {}
    bool less(const EntryRef lhs, const EntryRef rhs) const noexcept override {
        bool lhs_neg_infty = !lhs.valid();
        bool rhs_neg_infty = !rhs.valid();
        return !rhs_neg_infty && (lhs_neg_infty || (strcmp(this->get(lhs), this->get(rhs)) < 0));
    }
    bool equal(const EntryRef lhs, const EntryRef rhs) const noexcept override {
        bool lhs_neg_infty = !lhs.valid();
        bool rhs_neg_infty = !rhs.valid();
        return (lhs_neg_infty && rhs_neg_infty) ||
               (!lhs_neg_infty && !rhs_neg_infty && (strcmp(this->get(lhs), this->get(rhs)) == 0));
    }
};

/**
 * Compare two strings based on entry refs.
 *
 * Valid entry ref is mapped to a string in a data store.
 * Invalid entry ref is mapped to positive infinity.
 */
template <typename RefT> class PositiveInfinityUniqueStoreStringComparator
    : public UniqueStoreStringComparator<RefT> {
public:
    PositiveInfinityUniqueStoreStringComparator(
        const typename UniqueStoreStringComparator<RefT>::DataStoreType& store) noexcept
        : UniqueStoreStringComparator<RefT>(store) {}
    bool less(const EntryRef lhs, const EntryRef rhs) const noexcept override {
        bool lhs_pos_infty = !lhs.valid();
        bool rhs_pos_infty = !rhs.valid();
        return !lhs_pos_infty && (rhs_pos_infty || (strcmp(this->get(lhs), this->get(rhs)) < 0));
    }
    bool equal(const EntryRef lhs, const EntryRef rhs) const noexcept override {
        bool lhs_pos_infty = !lhs.valid();
        bool rhs_pos_infty = !rhs.valid();
        return (lhs_pos_infty && rhs_pos_infty) ||
               (!lhs_pos_infty && !rhs_pos_infty && (strcmp(this->get(lhs), this->get(rhs)) == 0));
    }
};

} // namespace vespalib::datastore
