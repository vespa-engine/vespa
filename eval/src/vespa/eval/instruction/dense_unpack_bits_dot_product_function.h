// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Fused tensor function computing the dot product between a dense
 * 1-dimensional float/double/bfloat16 vector and a bit-packed int8 vector
 * that would otherwise need to be unpacked with 'unpack_bits'
 * first, i.e. a fused version of:
 *
 *   reduce(A * unpack_bits(B, <cell_type>, <bitorder>), sum, dim)
 *
 * The packed bits are never materialized as a separate (unpacked)
 * tensor; the dot product is computed directly from the packed
 * int8 source using a bitmask-driven select-and-add, since each
 * unpacked cell is always exactly 0 or 1.
 **/
class DenseUnpackBitsDotProductFunction : public tensor_function::Op2 {
    using Super = tensor_function::Op2;

private:
    bool _big_bitorder;

public:
    DenseUnpackBitsDotProductFunction(const TensorFunction& lhs_in, const TensorFunction& packed_in,
                                      bool big_bitorder);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory& factory, Stash& stash) const override;
    bool result_is_mutable() const override { return true; }
    void visit_self(vespalib::ObjectVisitor& visitor) const override;
    static const TensorFunction& optimize(const TensorFunction& expr, Stash& stash);
};

} // namespace vespalib::eval
