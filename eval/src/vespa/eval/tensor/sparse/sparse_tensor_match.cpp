// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_match.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <assert.h>

namespace vespalib::tensor {

void
SparseTensorMatch::fastMatch(const SparseTensor &lhs, const SparseTensor &rhs)
{
    const auto & lhs_map = lhs.index().get_map();
    const auto & rhs_map = rhs.index().get_map();
    _builder.reserve(lhs_map.size());
    for (const auto & kv : lhs_map) {
        auto rhsItr = rhs_map.find(kv.first);
        if (rhsItr != rhs_map.end()) {
            auto a = lhs.my_values()[kv.second];
            auto b = rhs.my_values()[rhsItr->second];
            _builder.insertCell(kv.first, a * b);
        }
    }
}

SparseTensorMatch::SparseTensorMatch(const SparseTensor &lhs, const SparseTensor &rhs)
    : _builder(lhs.fast_type())
{
    assert (lhs.fast_type().dimensions().size() == rhs.fast_type().dimensions().size());
    // Ensure that first tensor to fastMatch has fewest cells.
    if (lhs.my_values().size() <= rhs.my_values().size()) {
        fastMatch(lhs, rhs);
    } else {
        fastMatch(rhs, lhs);
    }
}

}
