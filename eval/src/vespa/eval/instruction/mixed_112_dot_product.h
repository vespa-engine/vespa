// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for the dot product between (the expansion of a 1d
 * sparse tensor and a 1d dense tensor) and (a 2d mixed tensor).
 */
class Mixed112DotProduct : public tensor_function::Node
{
private:
    Child _a; // 1d sparse
    Child _b; // 1d dense
    Child _c; // 2d mixed

public:
    Mixed112DotProduct(const TensorFunction &a_in,
                       const TensorFunction &b_in,
                       const TensorFunction &c_in);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    void push_children(std::vector<Child::CREF> &children) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
