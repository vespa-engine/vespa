// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function unpacking bits into separate values.
 *
 * Both the tensor containing the packed bits and the result tensor
 * must have cell type 'int8'. The bits must be unpacked in canonical
 * order; bytes are unpacked with increasing index, bits within a byte
 * are unpacked from most to least significant.
 *
 * The baseline expression looks like this:
 *
 * tensor<int8>(x[64])(bit(packed{x:(x/8)},7-(x%8)))
 *
 * in this case 'packed' must be a tensor with type
 * 'tensor<int8>(x[8])' (the inner result dimension is always 8 times
 * larger than the inner input dimension).
 *
 * Unpacking of bits from multi-dimensional tensors will currently not
 * be optimized.
 **/
class UnpackBitsFunction : public tensor_function::Op1
{
public:
    UnpackBitsFunction(const ValueType &res_type_in, const TensorFunction &packed);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
