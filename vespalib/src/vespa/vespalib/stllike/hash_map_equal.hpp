// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hash_map.h"

namespace vespalib {

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

}
