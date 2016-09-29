// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_product.h"
#include "sparse_tensor_address_decoder.h"
#include "sparse_tensor_address_combiner.h"
#include <type_traits>

namespace vespalib {
namespace tensor {

using sparse::TensorAddressCombiner;

void
SparseTensorProduct::bruteForceProduct(const TensorImplType &lhs,
                                          const TensorImplType &rhs)
{
    TensorAddressCombiner addressCombiner(lhs.dimensions(), rhs.dimensions());
    for (const auto &lhsCell : lhs.cells()) {
        for (const auto &rhsCell : rhs.cells()) {
            bool combineSuccess = addressCombiner.combine(lhsCell.first,
                                                          rhsCell.first);
            if (combineSuccess) {
                _builder.insertCell(addressCombiner.getAddressRef(),
                                    lhsCell.second * rhsCell.second);
            }
        }
    }
}


void
SparseTensorProduct::fastProduct(const TensorImplType &lhs,
                                    const TensorImplType &rhs)
{
    const typename TensorImplType::Cells &rhsCells = rhs.cells();
    for (const auto &lhsCell : lhs.cells()) {
        auto itr = rhsCells.find(lhsCell.first);
        if (itr != rhsCells.end()) {
            _builder.insertCell(lhsCell.first,
                                lhsCell.second * itr->second);
        }
    }
}


SparseTensorProduct::SparseTensorProduct(const TensorImplType &lhs,
                                               const TensorImplType &rhs)
    : Parent(lhs.combineDimensionsWith(rhs))
{
#if 0
    /* Commented ut for now since we want to see brute force performance. */
    // All dimensions are common
    if (lhs.dimensions().size() == rhs.dimensions().size() &&
        lhs.dimensions().size() == _builder.dimensions().size()) {
        fastProduct(lhs, rhs);
        return;
    }
    // TODO: Handle zero cells or zero dimensions cases
    // No dimensions are common
    if (lhs.dimensions().size() + rhs.dimensions().size() ==
        _builder.dimensions().size()) {
        bruteForceNoCommonDimensionProduct(lhs, rhs);
        return;
    }
    // lhs dimensions equals common dimensions
    if (rhs.dimensions().size() == _builder.dimensions().size()) {
    }
    // rhs dimensions equals common dimensions
    if (lhs.dimensions().size() == _builder.dimensions().size()) {
    }
#endif
    bruteForceProduct(lhs, rhs);
}

} // namespace vespalib::tensor
} // namespace vespalib
