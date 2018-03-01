// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for inplace map operation on mutable dense tensors.
 **/
class DenseInplaceMapFunction : public eval::tensor_function::Op1
{
public:
    using map_fun_t = ::vespalib::eval::tensor_function::map_fun_t;
private:
    map_fun_t _function;
public:
    DenseInplaceMapFunction(const eval::ValueType &result_type,
                            const eval::TensorFunction &child,
                            map_fun_t function_in);
    ~DenseInplaceMapFunction();
    map_fun_t function() const { return _function; }
    bool result_is_mutable() const override { return true; }
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
