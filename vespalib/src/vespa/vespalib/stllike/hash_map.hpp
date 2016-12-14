// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hash_map.h"
#include "hashtable.hpp"

namespace vespalib {

template <typename K, typename V, typename H, typename EQ, typename M>
hash_map<K, V, H, EQ, M>::hash_map(size_t reserveSize) :
    _ht(reserveSize)
{ }

template <typename K, typename V, typename H, typename EQ, typename M>
hash_map<K, V, H, EQ, M>::~hash_map() { }

template <typename K, typename V, typename H, typename EQ, typename M>
bool
hash_map<K, V, H, EQ, M>::operator ==(const hash_map & rhs) const {
    bool identical(rhs.size() == size());
    if (identical) {
        for(const_iterator at(begin()), mat(end()); identical && at != mat; at++) {
            const_iterator bt = rhs.find(at->first);
            identical = (bt != rhs.end()) && (*at == *bt);
        }
    }
    return identical;
}

template <typename K, typename V, typename H, typename EQ, typename M>
typename hash_map<K, V, H, EQ, M>::insert_result
hash_map<K, V, H, EQ, M>::insert(const value_type & value) {
    return _ht.insert(value);
}

template <typename K, typename V, typename H, typename EQ, typename M>
void
hash_map<K, V, H, EQ, M>::erase(const K & key) {
    return _ht.erase(key);
}

template <typename K, typename V, typename H, typename EQ, typename M>
template <typename InputIt>
void
hash_map<K, V, H, EQ, M>::insert(InputIt first, InputIt last) {
    while (first != last) {
        _ht.insert(*first);
        ++first;
    }
}

template <typename K, typename V, typename H, typename EQ, typename M>
void
hash_map<K, V, H, EQ, M>::clear() {
    _ht.clear();
}

template <typename K, typename V, typename H, typename EQ, typename M>
void
hash_map<K, V, H, EQ, M>::resize(size_t newSize) {
    _ht.resize(newSize);
}

template <typename K, typename V, typename H, typename EQ, typename M>
void
hash_map<K, V, H, EQ, M>::swap(hash_map & rhs) {
    _ht.swap(rhs._ht);
}

template <typename K, typename V, typename H, typename EQ, typename M>
size_t
hash_map<K, V, H, EQ, M>::getMemoryConsumption() const {
    return _ht.getMemoryConsumption();
}

template <typename K, typename V, typename H, typename EQ, typename M>
size_t
hash_map<K, V, H, EQ, M>::getMemoryUsed() const
{
    return _ht.getMemoryUsed();
}

}

#define VESPALIB_HASH_MAP_INSTANTIATE(K, V) \
    template class vespalib::hash_map<K, V>; \
    template class vespalib::hashtable<K, std::pair<K,V>, vespalib::hash<K>, std::equal_to<K>, std::_Select1st<std::pair<K,V>>>; \
    template vespalib::hashtable<K, std::pair<K,V>, vespalib::hash<K>, std::equal_to<K>, std::_Select1st<std::pair<K,V>>>::insert_result \
             vespalib::hashtable<K, std::pair<K,V>, vespalib::hash<K>, std::equal_to<K>, std::_Select1st<std::pair<K,V>>>::insert(std::pair<K,V> &&); \
    template class vespalib::Array<vespalib::hash_node<std::pair<K,V>>>;
