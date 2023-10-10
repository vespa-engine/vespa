// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hash_map.h"

namespace vespalib {

template <typename K, typename V, typename H, typename EQ, typename M>
template <typename InputIt>
void
hash_map<K, V, H, EQ, M>::insert(InputIt first, InputIt last) {
    while (first != last) {
        insert(*first);
        ++first;
    }
}

}
