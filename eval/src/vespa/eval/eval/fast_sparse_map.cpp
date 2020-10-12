// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_sparse_map.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::eval {

FastSparseMap::~FastSparseMap() = default;

}

VESPALIB_HASH_MAP_INSTANTIATE_H_E(vespalib::eval::FastSparseMap::Key, uint32_t, vespalib::eval::FastSparseMap::Hash, vespalib::eval::FastSparseMap::Equal);
