// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "sparse_tensor_t.h"
#include "sparse_tensor_address_builder.h"
#include <vespa/eval/tensor/tensor_visitor.h>

namespace vespalib::tensor {

/**
 * This class handles a tensor remove operation on a sparse tensor.
 *
 * Creates a new tensor by removing the cells matching the cell addresses visited.
 * The value associated with the address is ignored.
 */
template<typename T> 
class SparseTensorRemove : public TensorVisitor {
private:
    const SparseTensorT<T> & _input;
    SparseTensorIndex::IndexMap _map;
    SparseTensorAddressBuilder _addressBuilder;
public:
    explicit SparseTensorRemove(const SparseTensorT<T> &input);
    ~SparseTensorRemove();
    void visit(const TensorAddress &address, double value) override;
    std::unique_ptr<Tensor> build();
};

}
