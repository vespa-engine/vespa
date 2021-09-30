// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function for a dot product inside a mixed tensor.
 *
 * Optimized tensor function for dot-product inside a bigger (possibly
 * mixed) tensor.  To trigger this, the function must be in the form
 * reduce((mixed tensor)*(vector),sum,dimension names)
 * with "vector" being a dense tensor with the same dimensions that
 * are reduced, "mixed tensor" must contain all these dimension, and
 * they must also be the innermost (alphabetically last) indexed
 * dimensions in the mixed tensor.
 * Simple example:
 *   mixed: tensor(category{},x[32])
 *   vector: tensor(x[32])
 *   expression: reduce(mixed*vector,sum,x)
 *   result: tensor(category{})
 * More complex example:
 *   mixed: tensor<double>(a{},b[31],c{},d[42],e{},f[5],g{})
 *   vector: tensor<float>(d[42],f[5])
 *   expression: reduce(mixed*vector,sum,d,f)
 *   result: tensor<double>(a{},b[31],c{},e{},g{})
 * Note:
 * if the bigger tensor is dense, other optimizers are likely
 * to pick up the operation, even if this function could also
 * handle them.
 **/
class MixedInnerProductFunction : public tensor_function::Op2
{
public:
    MixedInnerProductFunction(const ValueType &res_type_in,
                              const TensorFunction &mixed_child,
                              const TensorFunction &vector_child);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static bool compatible_types(const ValueType &res, const ValueType &mixed, const ValueType &dense);
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
