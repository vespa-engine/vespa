// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::tensor {

/**
 * Tensor function for inplace join operation on mutable dense tensors.
 **/
class DenseInplaceJoinFunction : public eval::tensor_function::Join
{
    using Super = eval::tensor_function::Join;
public:
    using join_fun_t = ::vespalib::eval::tensor_function::join_fun_t;
private:
    bool _write_left;
public:
    DenseInplaceJoinFunction(const eval::ValueType &result_type,
                             const TensorFunction &lhs,
                             const TensorFunction &rhs,
                             join_fun_t function_in,
                             bool write_left_in);
    ~DenseInplaceJoinFunction();
    bool write_left() const { return _write_left; }
    bool result_is_mutable() const override { return true; }
    eval::InterpretedFunction::Instruction compile_self(Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::tensor
