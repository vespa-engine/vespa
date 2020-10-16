// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include "sparse_tensor.h"
#include "sparse_tensor_t.h"
#include "sparse_tensor_address_builder.h"

namespace vespalib::tensor {

/*
 * This class handles tensor modify update on a sparse tensor.
 * For all cells visited, a join function is applied to determine
 * the new cell value.
 */
template<typename T>
class SparseTensorModify : public TensorVisitor
{
    using join_fun_t = vespalib::eval::operation::op2_t;
    join_fun_t                 _op;
    eval::ValueType            _type;
    const SparseTensorIndex &  _index;
    std::vector<T>             _values;
    SparseTensorAddressBuilder _addressBuilder;

public:
    SparseTensorModify(join_fun_t op, const SparseTensorT<T> & input);
    ~SparseTensorModify();
    void visit(const TensorAddress &address, double value) override;
    std::unique_ptr<Tensor> build();
};

}
