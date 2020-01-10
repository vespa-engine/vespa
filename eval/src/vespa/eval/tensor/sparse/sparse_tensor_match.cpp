// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_match.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <assert.h>

namespace vespalib::tensor {

void
SparseTensorMatch::fastMatch(const TensorImplType &lhs, const TensorImplType &rhs)
{
    _builder.reserve(lhs.cells().size());
    for (const auto &lhsCell : lhs.cells()) {
        auto rhsItr = rhs.cells().find(lhsCell.first);
        if (rhsItr != rhs.cells().end()) {
            _builder.insertCell(lhsCell.first, lhsCell.second * rhsItr->second);
        }
    }
}

SparseTensorMatch::SparseTensorMatch(const TensorImplType &lhs, const TensorImplType &rhs)
    : Parent(lhs.combineDimensionsWith(rhs))
{
    assert (lhs.fast_type().dimensions().size() == rhs.fast_type().dimensions().size());
    assert (lhs.fast_type().dimensions().size() == _builder.fast_type().dimensions().size());

    // Ensure that first tensor to fastMatch has fewest cells.
    if (lhs.cells().size() <= rhs.cells().size()) {
        fastMatch(lhs, rhs);
    } else {
        fastMatch(rhs, lhs);
    }
}

}
