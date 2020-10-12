// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include "dense_tensor_view.h"

namespace vespalib::tensor {

/*
 * This class handles tensor modify update on a dense tensor.
 * For all cells visited, a join function is applied to determine
 * the new cell value.
 */
template <class CT>
class DenseTensorModify : public TensorVisitor
{
    using join_fun_t = vespalib::eval::operation::op2_t;
    join_fun_t             _op;
    const eval::ValueType &_type;
    std::vector<CT>        _cells;

public:
    DenseTensorModify(join_fun_t op, const eval::ValueType &type, std::vector<CT> &&cells);
    ~DenseTensorModify();
    void visit(const TensorAddress &address, double value) override;
    std::unique_ptr<Tensor> build();
};

}
