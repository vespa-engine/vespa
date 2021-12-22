// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for the dot product between the expansion of two 1d
 * sparse tensors and a 2d sparse tensor.
 */
class Sparse112DotProduct : public tensor_function::Node
{
private:
    Child _a;
    Child _b;
    Child _c;

public:
    Sparse112DotProduct(const TensorFunction &a_in,
                        const TensorFunction &b_in,
                        const TensorFunction &c_in);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    void push_children(std::vector<Child::CREF> &children) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
