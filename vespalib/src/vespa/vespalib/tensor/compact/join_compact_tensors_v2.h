// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace tensor {

/*
 * Join the cells of two tensors.
 * The given function is used to calculate the resulting cell value for overlapping cells.
 */
template <typename Function>
Tensor::UP
joinCompactTensorsV2(const CompactTensorV2 &lhs, const CompactTensorV2 &rhs,
                     Function &&func)
{
    DirectTensorBuilder<CompactTensorV2> builder(lhs.combineDimensionsWith(rhs),
                                                 lhs.cells(), lhs.dimensions());
    if (builder.dimensions().size() == rhs.dimensions().size()) {
        for (const auto &rhsCell : rhs.cells()) {
            builder.insertCell(rhsCell.first, rhsCell.second, func);
        }
    } else {
        CompactTensorV2AddressPadder addressPadder(builder.dimensions(),
                                                   rhs.dimensions());
        for (const auto &rhsCell : rhs.cells()) {
            addressPadder.padAddress(rhsCell.first);
            builder.insertCell(addressPadder, rhsCell.second, func);
        }
    }
    return builder.build();
}

/*
 * Join the cells of two tensors, where the rhs values are treated as negated values.
 * The given function is used to calculate the resulting cell value for overlapping cells.
 */
template <typename Function>
Tensor::UP
joinCompactTensorsV2Negated(const CompactTensorV2 &lhs,
                            const CompactTensorV2 &rhs,
                            Function &&func)
{
    DirectTensorBuilder<CompactTensorV2> builder(lhs.combineDimensionsWith(rhs),
                                                 lhs.cells(), lhs.dimensions());
    if (builder.dimensions().size() == rhs.dimensions().size()) {
        for (const auto &rhsCell : rhs.cells()) {
            builder.insertCell(rhsCell.first, -rhsCell.second, func);
        }
    } else {
        CompactTensorV2AddressPadder addressPadder(builder.dimensions(),
                                                   rhs.dimensions());
        for (const auto &rhsCell : rhs.cells()) {
            addressPadder.padAddress(rhsCell.first);
            builder.insertCell(addressPadder, -rhsCell.second, func);
        }
    }
    return builder.build();
}

} // namespace vespalib::tensor
} // namespace vespalib
