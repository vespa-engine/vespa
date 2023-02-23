// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "fast_addr_map.h"

namespace vespalib::eval {

/*
 * Tensor value index, used to map labels to dense subspace indexes.
 *
 * This is the class instructions will look for when optimizing sparse
 * operations by calling inline functions directly.
 */
struct FastValueIndex final : Value::Index {
    FastAddrMap map;
    FastValueIndex(size_t num_mapped_dims_in, const StringIdVector &labels, size_t expected_subspaces_in)
        : map(num_mapped_dims_in, labels, expected_subspaces_in) {}
    size_t size() const override { return map.size(); }
    std::unique_ptr<View> create_view(ConstArrayRef<size_t> dims) const override;
};

}
