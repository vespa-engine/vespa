// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>

namespace vespalib::eval {

/**
 * Tensor function for simple join operations between a primary and a
 * secondary tensor that may be evaluated in-place if the primary
 * tensor is mutable and has the same cell-type as the result.
 *
 * The secondary tensor must be dense and contain a subset of the
 * dimensions present in the dense subspace of the primary tensor. The
 * common dimensions must have a simple overlap pattern ('inner',
 * 'outer' or 'full'). The primary tensor may be mixed, in which case
 * the index will be forwarded to the result.
 **/
class MixedSimpleJoinFunction : public tensor_function::Join
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
    MixedSimpleJoinFunction(const ValueType &result_type,
                            const TensorFunction &lhs,
                            const TensorFunction &rhs,
                            join_fun_t function_in,
                            Primary primary_in,
                            Overlap overlap_in);
    ~MixedSimpleJoinFunction() override;
    Primary primary() const { return _primary; }
    Overlap overlap() const { return _overlap; }
    bool primary_is_mutable() const;
    size_t factor() const;
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval
