// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for multiple dense matrix multiplications. This is
 * an extension to normal matrix multiplication where the tensors
 * combined may have more than 2 dimensions.
 **/
class DenseMultiMatMulFunction : public tensor_function::Op2
{
    using Super = tensor_function::Op2;
private:
    size_t _lhs_size;
    size_t _common_size;
    size_t _rhs_size;
    size_t _matmul_cnt;
    bool   _lhs_common_inner;
    bool   _rhs_common_inner;

public:
    DenseMultiMatMulFunction(const ValueType &result_type,
                             const TensorFunction &lhs_in,
                             const TensorFunction &rhs_in,
                             size_t lhs_size,
                             size_t common_size,
                             size_t rhs_size,
                             size_t matmul_cnt,
                             bool lhs_common_inner,
                             bool rhs_common_inner);
    ~DenseMultiMatMulFunction() override;

    bool result_is_mutable() const override { return true; }

    size_t lhs_size() const { return _lhs_size; }
    size_t common_size() const { return _common_size; }
    size_t rhs_size() const { return _rhs_size; }
    size_t matmul_cnt() const { return _matmul_cnt; }
    bool lhs_common_inner() const { return _lhs_common_inner; }
    bool rhs_common_inner() const { return _rhs_common_inner; }

    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
