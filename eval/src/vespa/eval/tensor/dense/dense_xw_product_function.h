// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include "dense_tensor_view.h"

namespace vespalib::tensor {

/**
 * Tensor function for product of one 1-dimensional and one 2-dimensional dense tensor.
 */
class DenseXWProductFunction : public eval::tensor_function::Op2
{
    using Super = eval::tensor_function::Op2;
public:
    struct Self {
        eval::ValueType result_type;
        size_t vector_size;
        size_t result_size;
        Self(const eval::ValueType &result_type_in,
             size_t vector_size_in, size_t result_size_in);
        ~Self();
    };

private:
    size_t _vector_size;
    size_t _result_size;
    bool _common_inner;

public:
    DenseXWProductFunction(const eval::ValueType &result_type,
                           const eval::TensorFunction &vector_in,
                           const eval::TensorFunction &matrix_in,
                           size_t vector_size,
                           size_t result_size,
                           bool common_inner);

    ~DenseXWProductFunction() {}

    bool result_is_mutable() const override { return true; }

    size_t vector_size() const { return _vector_size; }
    size_t result_size() const { return _result_size; }
    bool common_inner() const { return _common_inner; }

    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor

