// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include "dense_tensor_view.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

namespace vespalib::tensor {

/**
 * Tensor function for dense matrix multiplication.
 **/
class DenseMatMulFunction : public eval::tensor_function::Op2
{
    using Super = eval::tensor_function::Op2;
public:
    struct Self {
        eval::ValueType result_type;
        size_t lhs_size;
        size_t common_size;
        size_t rhs_size;
        hwaccelrated::IAccelrated::UP hw;
        Self(const eval::ValueType &result_type_in,
             size_t lhs_size_in, size_t common_size_in, size_t rhs_size_in);
        ~Self();
    };

private:
    size_t _lhs_size;
    size_t _common_size;
    size_t _rhs_size;
    bool   _lhs_common_inner;
    bool   _rhs_common_inner;

public:
    DenseMatMulFunction(const eval::ValueType &result_type,
                        const eval::TensorFunction &lhs_in,
                        const eval::TensorFunction &rhs_in,
                        size_t lhs_size,
                        size_t common_size,
                        size_t rhs_size,
                        bool lhs_common_inner,
                        bool rhs_common_inner);
    ~DenseMatMulFunction();

    bool result_is_mutable() const override { return true; }

    size_t lhs_size() const { return _lhs_size; }
    size_t common_size() const { return _common_size; }
    size_t rhs_size() const { return _rhs_size; }
    bool lhs_common_inner() const { return _lhs_common_inner; }
    bool rhs_common_inner() const { return _rhs_common_inner; }

    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
