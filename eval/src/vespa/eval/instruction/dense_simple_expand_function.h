// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>

namespace vespalib::eval {

/**
 * Tensor function for simple expanding join operations on dense
 * tensors. An expanding operation is a join between tensors resulting
 * in a larger tensor where the input tensors have no matching
 * dimensions (trivial dimensions are ignored). A simple expanding
 * operation is an expanding operation where all the dimensions of one
 * input is nested inside all the dimensions from the other input
 * within the result (trivial dimensions are again ignored).
 **/
class DenseSimpleExpandFunction : public tensor_function::Join
{
    using Super = tensor_function::Join;
public:
    enum class Inner : uint8_t { LHS, RHS };
    using join_fun_t = operation::op2_t;
private:
    Inner _inner;
public:
    DenseSimpleExpandFunction(const ValueType &result_type,
                              const TensorFunction &lhs,
                              const TensorFunction &rhs,
                              join_fun_t function_in,
                              Inner inner_in);
    ~DenseSimpleExpandFunction() override;
    Inner inner() const { return _inner; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
