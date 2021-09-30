// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function combining multiple dot products with multiple
 * layers of aggregation, resulting in a single scalar result.
 * 
 * inputs:
 *   query:    tensor<float>(qt{},x[32])
 *   document: tensor<float>(dt{},x[32])
 *
 * expression:
 *   reduce(
 *     reduce(
 *       reduce(query * document, sum, x),
 *       max, dt
 *     ),
 *     sum, qt
 *   )
 *
 * Both query and document contains a collection of vectors. For each
 * query vector, take the dot product with all document vectors and
 * select the maximum result. Sum these partial results into the final
 * result value.
 *
 * Note that not all equivalent forms are matched by this function
 * (initial matching will be very specific).
 **/
class SumMaxDotProductFunction : public tensor_function::Op2
{
private:
    size_t _dp_size;
public:
    SumMaxDotProductFunction(const ValueType &res_type_in,
                             const TensorFunction &query,
                             const TensorFunction &document,
                             size_t dp_size);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    size_t dp_size() const { return _dp_size; }
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
