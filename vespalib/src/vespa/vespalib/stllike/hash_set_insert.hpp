// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hash_set.h"

namespace vespalib {

template<typename K, typename H, typename EQ, typename M>
template<typename InputIterator>
hash_set<K, H, EQ, M>::hash_set(InputIterator first, InputIterator last)
    : _ht(last - first)
{
    insert(first, last);
}

template<typename K, typename H, typename EQ, typename M>
template<typename InputIt>
void
hash_set<K, H, EQ, M>::insert(InputIt first, InputIt last) {
    for (; first < last; first++) {
        insert(*first);
    }
}

}
