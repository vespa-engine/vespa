// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_sparse_map.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::eval {

SimpleSparseMap::~SimpleSparseMap() = default;

}

VESPALIB_HASH_MAP_INSTANTIATE_H_E(vespalib::eval::SimpleSparseMap::Key, uint32_t, vespalib::eval::SimpleSparseMap::Hash, vespalib::eval::SimpleSparseMap::Equal);
