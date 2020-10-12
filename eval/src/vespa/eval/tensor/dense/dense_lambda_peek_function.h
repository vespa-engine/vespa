// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function creating a new dense tensor based on peeking cells
 * of a single existing dense tensor. Which cells to peek is described
 * by a single (compilable) function mapping the individual dimension
 * indexes of the tensor to be created into global cell indexes of the
 * tensor to be peeked.
 **/
class DenseLambdaPeekFunction : public eval::tensor_function::Op1
{
private:
    std::shared_ptr<eval::Function const> _idx_fun;

public:
    DenseLambdaPeekFunction(const eval::ValueType &result_type,
                            const eval::TensorFunction &child,
                            std::shared_ptr<eval::Function const> idx_fun);
    ~DenseLambdaPeekFunction() override;
    eval::InterpretedFunction::Instruction compile_self(eval::EngineOrFactory engine, Stash &stash) const override;
    vespalib::string idx_fun_dump() const;
    bool result_is_mutable() const override { return true; }
};

} // namespace vespalib::tensor
