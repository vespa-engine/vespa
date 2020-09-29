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
    eval::ValueType        _type;
    SparseTensorIndex      _index;
    std::vector<double>    _values;
    SparseTensorAddressBuilder _addressBuilder;
public:
    SparseTensorAdd(eval::ValueType type, SparseTensorIndex index, std::vector<double> values);
    ~SparseTensorAdd();
    void visit(const TensorAddress &address, double value) override;
    std::unique_ptr<Tensor> build();
};

}
