// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function unpacking bits into separate values.
 *
 * The tensor containing the packed bits must be a vector (dense
 * tensor with 1 dimension) with cell type 'int8'. Bytes must be
 * processed with increasing index. Bits may be unpacked in either
 * 'big' or 'little' order. The result must be a vector (dense tensor
 * with 1 dimension) where the dimension is 8 times larger than the
 * input (since there are 8 bits packed into each int8 value).
 *
 * Baseline expression for 'big' bitorder (most significant bit first):
 * (Note: this is the default order used by numpy unpack_bits)
 * 'tensor<int8>(x[64])(bit(packed{x:(x/8)},7-(x%8)))'
 *
 * Baseline expression for 'little' bitorder (least significant bit first):
 * (Note: make sure this is the actual order of your bits)
 * 'tensor<int8>(x[64])(bit(packed{x:(x/8)},x%8))'
 **/
class UnpackBitsFunction : public tensor_function::Op1
{
private:
    bool _big_bitorder;
public:
    UnpackBitsFunction(const ValueType &res_type_in, const TensorFunction &packed, bool big);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
