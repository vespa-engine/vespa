// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>

namespace vespalib::tensor {

/*
 * A collection of tensor cells, used as argument for modifying a subset
 * of cells in a tensor.
 */
class CellValues {
    const SparseTensor &_tensor;

public:
    CellValues(const SparseTensor &tensor)
        : _tensor(tensor)
    {
    }

    void accept(TensorVisitor &visitor) const {
        _tensor.accept(visitor);
    }

    eval::TensorSpec toSpec() const {
        return _tensor.toSpec();
    }
};

}
