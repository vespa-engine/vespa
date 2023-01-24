// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for joining a general tensor with a number
 */
class JoinWithNumberFunction : public tensor_function::Op2
{
public:
    enum class Primary : uint8_t { LHS, RHS };
private:
    using Super = tensor_function::Op2;
    Primary _primary;
    tensor_function::join_fun_t _function;
public:

    JoinWithNumberFunction(const tensor_function::Join &original_join, bool number_on_left);
    ~JoinWithNumberFunction();
    Primary primary() const { return _primary; }
    bool primary_is_mutable() const;
    bool result_is_mutable() const override { return true; }

    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace vespalib::eval

