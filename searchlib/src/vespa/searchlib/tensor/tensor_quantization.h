// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/quant/eden.h>

#include <memory>

/*
 * Generalized support for quantizing and dequantizing indexed and mixed float32 tensors.
 *
 * We quantize each dense subspace individually, treating each such subspace as if it's logically
 * a single vector whose size is the product of all indexed dimension sizes. This allows for
 * maximally dense bit packing _across_ indexed dimensions (for one particular dense subspace),
 * and also allows for selectively dequantizing individual _mapped_ subspaces.
 *
 * This packing means that a quantized tensor has a differently shaped type than the original
 * float tensor. All mapped dimensions are preserved as-is, but all indexed dimensions are
 * replaced with a single "aggregate" int8 indexed dimension whose size is equal to the number
 * of bytes required to store a single full, dense subspace. The number of bytes required
 * is proportional to the number of quantization bits used. To ensure dimension name uniqueness
 * within the transformed type, we reuse the name of the last (in lexicographic order) indexed
 * dimension.
 *
 * Quantization of fully sparse tensors is not supported.
 */

namespace vespalib::eval {
struct Value;
} // namespace vespalib::eval

namespace search::tensor {

/*
 * Returns a transformed ValueType that can be used to store a quantized representation of `tensor_type`
 * using the quantization parameters specified by `quantizer`.
 *
 * The tensor type must contain at least 1 indexed dimension.
 */
[[nodiscard]] vespalib::eval::ValueType to_quantized_tensor_type(const vespalib::eval::ValueType& f32_tensor_type,
                                                                 const vespalib::quant::EdenQuantizer& quantizer);

/*
 * Returns a quantized representation of the input `f32_in_tensor`, using the specified quantization mode.
 *
 * Preconditions:
 *   - `quantizer` must be logically the same as that provided as input to `to_quantized_tensor_type()`
 *      (i.e. it must have the same construction parameters, but need not be the same actual instance).
 *   - `f32_in_tensor.type()` must be the same as that provided as input to `to_quantized_tensor_type()`.
 *   - `quantized_type` must be the same as the output of `to_quantized_tensor_type()`.
 */
[[nodiscard]] std::unique_ptr<vespalib::eval::Value>
quantize_f32_tensor(const vespalib::eval::Value& f32_in_tensor, const vespalib::eval::ValueType& quantized_type,
                    vespalib::quant::EdenQuantizer& quantizer, vespalib::quant::QuantMode quantization_mode);

/*
 * Preconditions:
 *   - `quantized_in_tensor` must contain tensor data that was produced by a prior call to
 *     `quantize_f32_tensor()`.
 *   - `out_f32_tensor_type` must match the type of the tensor that was originally provided
 *      as `f32_in_tensor` to the `quantize_f32_tensor` function.
 *   - `quantizer` must be logically the same as the one used in the prior `quantize_f32_tensor` call.
 */
[[nodiscard]] std::unique_ptr<vespalib::eval::Value>
dequantize_tensor(const vespalib::eval::Value&     quantized_in_tensor,
                  const vespalib::eval::ValueType& out_f32_tensor_type, vespalib::quant::EdenQuantizer& quantizer);

} // namespace search::tensor
