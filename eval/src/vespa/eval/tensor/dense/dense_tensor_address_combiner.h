// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>
#include "dense_tensor_cells_iterator.h"

namespace vespalib {
namespace tensor {


/**
 * Combines two dense tensor addresses to a new tensor address.
 * The resulting dimensions is the union of the input dimensions and
 * common dimensions must have matching labels.
 */
class DenseTensorAddressCombiner
{
public:
    using Address = std::vector<size_t>;

private:
    enum class AddressOp {
        LHS,
        RHS,
        BOTH
    };

    using CellsIterator = DenseTensorCellsIterator;

    std::vector<AddressOp> _ops;
    Address _combinedAddress;

public:
    DenseTensorAddressCombiner(const eval::ValueType &lhs,
                               const eval::ValueType &rhs);

    bool combine(const CellsIterator &lhsItr,
                 const CellsIterator &rhsItr);
    const Address &address() const { return _combinedAddress; }

    static eval::ValueType combineDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs);

};

} // namespace vespalib::tensor
} // namespace vespalib
