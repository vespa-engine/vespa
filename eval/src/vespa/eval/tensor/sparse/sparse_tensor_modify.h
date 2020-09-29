// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor_visitor.h>
#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"

namespace vespalib::tensor {

/*
 * This class handles tensor modify update on a sparse tensor.
 * For all cells visited, a join function is applied to determine
 * the new cell value.
 */
class SparseTensorModify : public TensorVisitor
{
    using join_fun_t = Tensor::join_fun_t;
    join_fun_t             _op;
    eval::ValueType        _type;
    SparseTensorIndex      _index;
    std::vector<double>    _values;
    SparseTensorAddressBuilder _addressBuilder;

public:
    SparseTensorModify(join_fun_t op, eval::ValueType type, SparseTensorIndex index, std::vector<double> values);
    ~SparseTensorModify();
    void visit(const TensorAddress &address, double value) override;
    std::unique_ptr<Tensor> build();
};

}
