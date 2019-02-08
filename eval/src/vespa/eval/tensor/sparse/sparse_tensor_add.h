// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include <vespa/eval/tensor/tensor_visitor.h>

namespace vespalib::tensor {

/**
 * This class handles a tensor add operation on a sparse tensor.
 *
 * Creates a new tensor by adding the cells of the argument tensor to this tensor.
 * Existing cell values are overwritten.
 */
class SparseTensorAdd : public TensorVisitor
{
    using Cells = SparseTensor::Cells;
    eval::ValueType _type;
    Cells _cells;
    Stash _stash;
    SparseTensorAddressBuilder _addressBuilder;

public:
    SparseTensorAdd(const eval::ValueType &type, Cells &&cells, Stash &&stash);
    ~SparseTensorAdd();
    void visit(const TensorAddress &address, double value) override;
    std::unique_ptr<Tensor> build();
};

}
