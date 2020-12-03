// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for generic tensor lambda producing dense tensor
 * views directly. This is the catch-all fall-back used by the default
 * (production) tensor engine to avoid having a TensorSpec as an
 * intermediate result.
 **/
class DenseLambdaFunction : public eval::tensor_function::Leaf
{
    using Super = eval::tensor_function::Leaf;
private:
    const eval::tensor_function::Lambda &_lambda;
public:
    enum class EvalMode : uint8_t { COMPILED, INTERPRETED };
    DenseLambdaFunction(const eval::tensor_function::Lambda &lambda_in);
    ~DenseLambdaFunction() override;
    bool result_is_mutable() const override { return true; }
    EvalMode eval_mode() const;
    eval::InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

}
