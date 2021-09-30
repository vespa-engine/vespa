// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function creating a new dense tensor based on peeking cells
 * of a single existing dense tensor. Which cells to peek is described
 * by a single (compilable) function mapping the individual dimension
 * indexes of the tensor to be created into global cell indexes of the
 * tensor to be peeked.
 **/
class DenseLambdaPeekFunction : public tensor_function::Op1
{
private:
    std::shared_ptr<Function const> _idx_fun;

public:
    DenseLambdaPeekFunction(const ValueType &result_type,
                            const TensorFunction &child,
                            std::shared_ptr<Function const> idx_fun);
    ~DenseLambdaPeekFunction() override;
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    vespalib::string idx_fun_dump() const;
    bool result_is_mutable() const override { return true; }
};

} // namespace
