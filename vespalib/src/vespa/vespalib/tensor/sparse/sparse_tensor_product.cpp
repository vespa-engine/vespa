// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_product.h"
#include "sparse_tensor_address_decoder.h"
#include <type_traits>

namespace vespalib {
namespace tensor {

namespace {

enum class AddressOp
{
    LHS,
    RHS,
    BOTH
};

using CombineOps = std::vector<AddressOp>;

CombineOps
buildCombineOps(const TensorDimensions &lhs,
                const TensorDimensions &rhs)
{
    CombineOps ops;
    auto rhsItr = rhs.cbegin();
    auto rhsItrEnd = rhs.cend();
    for (auto &lhsDim : lhs) {
        while (rhsItr != rhsItrEnd && *rhsItr < lhsDim) {
            ops.push_back(AddressOp::RHS);
            ++rhsItr;
        }
        if (rhsItr != rhsItrEnd && *rhsItr == lhsDim) {
            ops.push_back(AddressOp::BOTH);
            ++rhsItr;
        } else {
            ops.push_back(AddressOp::LHS);
        }
    }
    while (rhsItr != rhsItrEnd) {
        ops.push_back(AddressOp::RHS);
        ++rhsItr;
    }
    return ops;
}


bool
combineAddresses(SparseTensorAddressBuilder &builder,
                 SparseTensorAddressRef lhsRef,
                 SparseTensorAddressRef rhsRef,
                 const CombineOps &ops)
{
    builder.clear();
    SparseTensorAddressDecoder lhs(lhsRef);
    SparseTensorAddressDecoder rhs(rhsRef);
    for (auto op : ops) {
        switch (op) {
        case AddressOp::LHS:
            builder.add(lhs.decodeLabel());
            break;
        case AddressOp::RHS:
            builder.add(rhs.decodeLabel());
            break;
        case AddressOp::BOTH:
            auto lhsLabel(lhs.decodeLabel());
            auto rhsLabel(rhs.decodeLabel());
            if (lhsLabel != rhsLabel) {
                return false;
            }
            builder.add(lhsLabel);
        }
    }
    assert(!lhs.valid());
    assert(!rhs.valid());
    return true;
}

}


void
SparseTensorProduct::bruteForceProduct(const TensorImplType &lhs,
                                          const TensorImplType &rhs)
{
    CombineOps ops(buildCombineOps(lhs.dimensions(), rhs.dimensions()));
    SparseTensorAddressBuilder addressBuilder;
    for (const auto &lhsCell : lhs.cells()) {
        for (const auto &rhsCell : rhs.cells()) {
            bool combineSuccess = combineAddresses(addressBuilder,
                                                   lhsCell.first, rhsCell.first,
                                                   ops);
            if (combineSuccess) {
                _builder.insertCell(addressBuilder.getAddressRef(),
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
