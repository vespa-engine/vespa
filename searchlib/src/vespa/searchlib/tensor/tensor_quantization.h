// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/quant/eden.h>

#include <memory>
#include <vector>

/*
 * Generalized support for quantizing and dequantizing indexed and mixed tensors.
 *
 * We quantize each dense subspace individually, treating each such subspace as if it's logically
 * a single vector whose size is the product of all indexed dimension sizes. This allows for
 * maximally dense bit packing _across_ indexed dimensions (for one particular dense subspace),
 * and also allows for selectively dequantizing individual _mapped_ subspaces.
 *
 * This packing means that a quantized tensor has a differently shaped type than the original
 * input tensor. All mapped dimensions are preserved as-is, but all indexed dimensions are
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
[[nodiscard]] vespalib::eval::ValueType to_quantized_tensor_type(const vespalib::eval::ValueType&      in_tensor_type,
                                                                 const vespalib::quant::EdenQuantizer& quantizer);

/*
 * Returns a quantized representation of the input `in_tensor`, using the specified quantization mode.
 *
 * Quantization internally operates on float vectors, so if `in_tensor` has a cell type other than
 * FLOAT the `scratch_space` vector will be resized to the combined dense input subspace size and
 * used as a temporary conversion buffer. Cells of type DOUBLE will be narrowed (possible precision
 * loss), all other types (BFLOAT16, INT8) will be widened (lossless).
 *
 * `scratch_space` is _not_ touched if the input cell type is FLOAT.
 *
 * Preconditions:
 *   - `quantizer` must be logically the same as that provided as input to `to_quantized_tensor_type()`
 *      (i.e. it must have the same construction parameters, but need not be the same actual instance).
 *   - `in_tensor.type()` must be the same as that provided as input to `to_quantized_tensor_type()`.
 *   - `quantized_type` must be the same as the output of `to_quantized_tensor_type()`.
 */
[[nodiscard]] std::unique_ptr<vespalib::eval::Value> quantize_tensor(const vespalib::eval::Value&     in_tensor,
                                                                     const vespalib::eval::ValueType& quantized_type,
                                                                     vespalib::quant::EdenQuantizer&  quantizer,
                                                                     vespalib::quant::QuantMode quantization_mode,
                                                                     std::vector<float>&        scratch_space);

/*
 * Returns a dequantized representation of the input `quantized_in_tensor`, with an output tensor
 * shape matching that of `out_tensor_type`.
 *
 * Dequantization internally operates on float vectors, so if `out_tensor_type` has a cell type
 * other than FLOAT the `scratch_space` vector will be resized to the combined dense output
 * subspace size and used as a temporary conversion buffer. Note that int8 output tensors are
 * converted by floating point _rounding_ (not truncation) followed by saturation to [-128, 127].
 *
 * `scratch_space` is _not_ touched if the output cell type is FLOAT.
 *
 * Preconditions:
 *   - `quantized_in_tensor` must contain tensor data that was produced by a prior call to
 *     `quantize_tensor()`.
 *   - `out_tensor_type` must match the type of the tensor that was originally provided
 *      as `in_tensor` to the `quantize_tensor` function.
 *   - `quantizer` must be logically the same as the one used in the prior `quantize_tensor` call.
 */
[[nodiscard]] std::unique_ptr<vespalib::eval::Value>
dequantize_tensor(const vespalib::eval::Value& quantized_in_tensor, const vespalib::eval::ValueType& out_tensor_type,
                  vespalib::quant::EdenQuantizer& quantizer, std::vector<float>& scratch_space);


} // namespace search::tensor
