// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_addr_map.h"
#include <vespa/vespalib/stllike/hashtable.hpp>

namespace vespalib::eval {

FastAddrMap::FastAddrMap(size_t num_mapped_dims, const StringIdVector &labels_in, size_t expected_subspaces)
    : _labels(num_mapped_dims, labels_in),
      _map(expected_subspaces * 2, Hash(), Equal(_labels))
{}
FastAddrMap::~FastAddrMap() = default;

}
