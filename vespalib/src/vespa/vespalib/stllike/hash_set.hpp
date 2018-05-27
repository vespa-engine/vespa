// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hash_set_insert.hpp"
#include "hashtable.hpp"

namespace vespalib {

template<typename K, typename H, typename EQ, typename M>
hash_set<K, H, EQ, M>::hash_set(size_t reserveSize)
    : _ht(reserveSize)
{ }

template<typename K, typename H, typename EQ, typename M>
hash_set<K, H, EQ, M>::hash_set(size_t reserveSize, const H &hasher, const EQ &equal)
    : _ht(reserveSize, hasher, equal)
{ }

template<typename K, typename H, typename EQ, typename M>
hash_set<K, H, EQ, M>::hash_set(std::initializer_list<K> input)
    : _ht(0)
{
    insert(input.begin(), input.end());
}

template<typename K, typename H, typename EQ, typename M>
hash_set<K, H, EQ, M>::~hash_set() {}

template<typename K, typename H, typename EQ, typename M>
bool
hash_set<K, H, EQ, M>::operator==(const hash_set &rhs) const {
    bool equal = (size() == rhs.size());
    if (equal) {
        for (auto itr = begin(), endItr = end(); equal && itr != endItr; ++itr) {
            equal = (rhs.find(*itr) != rhs.end());
        }
    }
    return equal;
}

template<typename K, typename H, typename EQ, typename M>
void
hash_set<K, H, EQ, M>::clear() {
    _ht.clear();
}

template<typename K, typename H, typename EQ, typename M>
void
hash_set<K, H, EQ, M>::resize(size_t newSize) {
    _ht.resize(newSize);
}

template<typename K, typename H, typename EQ, typename M>
void
hash_set<K, H, EQ, M>::swap(hash_set &rhs) {
    _ht.swap(rhs._ht);
}

template<typename K, typename H, typename EQ, typename M>
size_t
hash_set<K, H, EQ, M>::getMemoryConsumption() const {
    return _ht.getMemoryConsumption();
}

template<typename K, typename H, typename EQ, typename M>
void
hash_set<K, H, EQ, M>::erase(const K &key) {
    return _ht.erase(key);
}

template<typename K, typename H, typename EQ, typename M>
typename hash_set<K, H, EQ, M>::insert_result
hash_set<K, H, EQ, M>::insert(const K & value) {
    return _ht.insert(value);
}

template<typename K, typename H, typename EQ, typename M>
typename hash_set<K, H, EQ, M>::insert_result
hash_set<K, H, EQ, M>::insert(K &&value) {
    return _ht.insert(std::move(value));
}

}

#define VESPALIB_HASH_SET_INSTANTIATE(K) \
    template class vespalib::hash_set<K>; \
    template class vespalib::hashtable<K, K, vespalib::hash<K>, std::equal_to<K>, std::_Identity<K>>; \
    template class vespalib::Array<vespalib::hash_node<K>>;

#define VESPALIB_HASH_SET_INSTANTIATE_H(K, H) \
    template class vespalib::hash_set<K, H>; \
    template class vespalib::hashtable<K, K, H, std::equal_to<K>, std::_Identity<K>>; \
    template class vespalib::Array<vespalib::hash_node<K>>;

