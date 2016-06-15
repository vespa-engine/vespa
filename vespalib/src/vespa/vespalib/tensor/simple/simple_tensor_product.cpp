// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "simple_tensor_product.h"
#include <vespa/vespalib/tensor/tensor_address_element_iterator.h>
#include <vespa/vespalib/tensor/dimensions_vector_iterator.h>
#include <vespa/vespalib/tensor/join_tensor_addresses.h>
#include <type_traits>

namespace vespalib {
namespace tensor {

namespace {

template <class Dimensions>
void
calcIntersectDimensions(DimensionsVector &res,
                        const Dimensions &lhs, const Dimensions &rhs)
{
    std::set_intersection(lhs.cbegin(), lhs.cend(), rhs.cbegin(), rhs.cend(),
                          std::back_inserter(res));
}


template <class Dimensions>
void
calcIntersectDimensions(DimensionsSet &res,
                        const Dimensions &lhs, const Dimensions &rhs)
{
    for (const auto &dimension : lhs) {
        if (std::binary_search(rhs.begin(), rhs.end(), dimension)) {
            res.insert(vespalib::stringref(dimension.c_str(),
                                           dimension.size()));
        }
    }
}


}


template <class DimensionsCollection>
void
SimpleTensorProduct::template bruteForceProduct(const TensorImplType &lhs,
                                                const TensorImplType &rhs)
{
    DimensionsCollection iDims;
    calcIntersectDimensions<Dimensions>(iDims,
                                        lhs.dimensions(), rhs.dimensions());
    AddressBuilderType combinedAddress;
    for (const auto &lhsCell : lhs.cells()) {
        for (const auto &rhsCell : rhs.cells()) {
            bool combineSuccess = joinTensorAddresses<AddressBuilderType,
                AddressType, AddressType>
                                  (combinedAddress, iDims,
                                   lhsCell.first, rhsCell.first);
            if (combineSuccess) {
                _builder.insertCell(combinedAddress, lhsCell.second * rhsCell.second);
            }
        }
    }
}


void
SimpleTensorProduct::fastProduct(const TensorImplType &lhs,
                                 const TensorImplType &rhs)
{
    const typename TensorImplType::Cells &rhsCells = rhs.cells();
    for (const auto &lhsCell : lhs.cells()) {
        auto itr = rhsCells.find(lhsCell.first);
        if (itr != rhsCells.end()) {
            _builder.insertCell(lhsCell.first, lhsCell.second * itr->second);
        }
    }
}


SimpleTensorProduct::SimpleTensorProduct(const TensorImplType &lhs,
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
#if 1
    // few common dimensions
    bruteForceProduct<DimensionsVector>(lhs, rhs);
#else
    // many common dimensions, too expensive to iterate through all of
    // them if each cell has relatively few dimensions.
    bruteForceProduct<DimensionsSet>(lhs, rhs);
#endif
}


} // namespace vespalib::tensor
} // namespace vespalib
