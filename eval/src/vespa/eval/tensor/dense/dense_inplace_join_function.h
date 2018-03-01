// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for inplace join operation on mutable dense tensors.
 **/
class DenseInplaceJoinFunction : public eval::tensor_function::Op2
{
public:
    using join_fun_t = ::vespalib::eval::tensor_function::join_fun_t;
private:
    join_fun_t _function;
    bool _left_is_mutable;
public:
    DenseInplaceJoinFunction(const eval::tensor_function::Join &orig, bool left_is_mutable);
    ~DenseInplaceJoinFunction();
    join_fun_t function() const { return _function; }
    bool result_is_mutable() const override { return true; }
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
