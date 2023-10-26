// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function combining multiple vector-based similarity measures
 * to find the best one. This function supports the following cases:
 *
 * - maximum dot product of vectors with float cell type (MaxSim)
 * - minimum hamming distance of bitvectors with int8 cell type
 *
 * The vectors used to calculate the individual distance metrics must
 * be the inner dense dimension of both inputs. The dimension reduced
 * to find the best similarity measure must be the remaining dimension
 * of one of the inputs.
 **/
class BestSimilarityFunction : public tensor_function::Op2
{
private:
    InterpretedFunction::op_function _my_fun;
    size_t _inner_size;
    uint64_t make_param(Stash &stash) const;
public:
    BestSimilarityFunction(const ValueType &res_type_in,
                           const TensorFunction &pri,
                           const TensorFunction &sec,
                           InterpretedFunction::op_function my_fun,
                           size_t inner_size);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
