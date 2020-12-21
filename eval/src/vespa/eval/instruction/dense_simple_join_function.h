// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>

namespace vespalib::eval {

/**
 * Tensor function for simple join operations on dense tensors.
 * TODO: consider if this is useful anymore, maybe we just need
 * to handle inplace.
 **/
class DenseSimpleJoinFunction : public tensor_function::Join
{
    using Super = tensor_function::Join;
public:
    enum class Primary : uint8_t { LHS, RHS };
    enum class Overlap : uint8_t { INNER, OUTER, FULL };
    using join_fun_t = operation::op2_t;
private:
    Primary _primary;
    Overlap _overlap;
public:
    DenseSimpleJoinFunction(const ValueType &result_type,
                            const TensorFunction &lhs,
                            const TensorFunction &rhs,
                            join_fun_t function_in,
                            Primary primary_in,
                            Overlap overlap_in);
    ~DenseSimpleJoinFunction() override;
    Primary primary() const { return _primary; }
    Overlap overlap() const { return _overlap; }
    bool primary_is_mutable() const;
    size_t factor() const;
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
