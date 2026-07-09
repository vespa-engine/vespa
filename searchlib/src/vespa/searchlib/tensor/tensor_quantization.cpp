// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_quantization.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/quant/eden.h>
#include <vespa/vespalib/util/bfloat16.h>

#include <algorithm>
#include <cassert>
#include <cmath>
#include <type_traits>

using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::Int8Float;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

template <typename T, typename T2>
[[nodiscard]] constexpr std::span<T> span_cast(const std::span<T2> in) noexcept {
    static_assert(sizeof(T) == sizeof(T2));
    static_assert(alignof(T) == alignof(T2));
    static_assert(std::is_trivially_copyable_v<T> && std::is_trivially_copyable_v<T2>);
    return std::span<T>(reinterpret_cast<T*>(in.data()), in.size());
}

template <typename FromType, typename ToType>
void copy_convert(std::span<const FromType> src, std::span<ToType> dst) noexcept __attribute__((noinline));

template <typename FromType, typename ToType>
void copy_convert(std::span<const FromType> src, std::span<ToType> dst) noexcept {
    ToType* p = dst.data();
    for (const FromType value : src) {
        *p++ = static_cast<ToType>(value);
    }
}

template <>
void copy_convert<vespalib::BFloat16, float>(std::span<const vespalib::BFloat16> src, std::span<float> dst) noexcept {
    vespalib::hwaccelerated::convert_bfloat16_to_float(reinterpret_cast<const uint16_t*>(src.data()), dst.data(),
                                                       dst.size());
}

template <>
void copy_convert<float, Int8Float>(std::span<const float> src, std::span<Int8Float> dst) noexcept {
    Int8Float* p = dst.data();
    // Since the dequantization process produces floating point numbers that are "approximately
    // near" the original int8 values, it makes intuitive sense then to emit the int8 values that
    // are closest to this approximation.
    // Note: this gets auto-vectorized on AArch64 (GCC 15), but not at all on x64 (again GCC; Clang
    // _does_ auto-vectorize this). Not sure why GCC doesn't use AVX2's _mm256_round_ps...
    // TODO manually vectorize? Highway saturating DemoteTo(i8, ConvertTo(i32, Round(f32_vec)))?
    for (const float value : src) {
        // We saturate the rounded value since it's not obviously guaranteed that we'll never
        // dequantize to floating point values outside the representable range of an int8.
        // Not doing this runs the risk of over-/underflowing.
        // Going via an i32 and clamping in the integer domain appears to generate better code
        // on AArch64 (explicit vector smax/smin instructions rather than fp comparisons).
        // TODO std::saturating_cast can be used on >= C++26
        *p++ = std::clamp(static_cast<int32_t>(std::round(value)), -128, 127);
    }
}

template <typename CT>
void convert_to_f32(std::span<const CT> src, std::span<float> dst) {
    static_assert(!std::is_same_v<CT, float>, "Trying to convert float to float; broken type conditions?");
    assert(dst.size() == src.size());
    copy_convert<CT, float>(src, dst);
}

template <typename CT>
void convert_from_f32(std::span<const float> src, std::span<CT> dst) {
    static_assert(!std::is_same_v<CT, float>, "Trying to convert float to float; broken type conditions?");
    assert(dst.size() == src.size());
    copy_convert<float, CT>(src, dst);
}

} // namespace

// TODO consider if we should just pass the number of bits rather than a full quantizer...
//  - could make size computation a static public function in EdenQuantizer

ValueType to_quantized_tensor_type(const ValueType& in_tensor_type, const vespalib::quant::EdenQuantizer& quantizer) {
    std::vector<ValueType::Dimension> q_dims = in_tensor_type.mapped_dimensions();
    assert(!in_tensor_type.is_sparse()); // Must have at least 1 indexed dimension
    assert(in_tensor_type.dense_subspace_size() == quantizer.dimensions());
    // Leave all sparse dimensions untouched and replace all indexed dimensions with a single
    // quantized "aggregate" dimension. We cheekily reuse the name of the last indexed dimension
    // to ensure we use a name that is unique within the tensor type.
    q_dims.emplace_back(in_tensor_type.indexed_dimensions().back().name, quantizer.quantized_size());
    return ValueType::make_type(CellType::INT8, std::move(q_dims));
}

// These functions have been cooked on firewood stol- uh, borrowed, from vespalib::eval::CopyValue. Yes. yes.

namespace {

struct QuantizeTensorWithImplicitInputConversion {
    template <typename InputCT>
    static std::unique_ptr<vespalib::eval::Value>
    invoke(const vespalib::eval::Value& in_tensor, const vespalib::eval::ValueType& quantized_type,
           vespalib::quant::EdenQuantizer& quantizer, const vespalib::quant::QuantMode quantization_mode,
           std::vector<float>& scratch_space) {
        // We expect that the number of mapped dimensions is the same for both tensor types
        const size_t num_mapped = quantized_type.count_mapped_dimensions();
        const size_t input_dense_size = in_tensor.type().dense_subspace_size();
        const size_t quantized_dense_size = quantized_type.dense_subspace_size();
        const auto&  idx = in_tensor.index();
        auto         input_cells = in_tensor.cells().typify<InputCT>();

        assert(input_dense_size == quantizer.dimensions());
        assert(quantized_dense_size == quantizer.quantized_size());
        assert(num_mapped == quantized_type.dimensions().size() - 1); // Indexed must be reduced down to 1 dimension

        auto builder = FastValueBuilderFactory::get().create_value_builder<Int8Float>(
            quantized_type, num_mapped, quantized_dense_size, idx.size());

        if constexpr (!std::is_same_v<InputCT, float>) {
            scratch_space.resize(input_dense_size);
        }
        std::vector<vespalib::string_id> addr(num_mapped);
        if (num_mapped == 0) {
            assert(idx.size() == 1);
            auto i8f_array_ref = builder->add_subspace(addr);
            auto u8_array_ref = span_cast<uint8_t>(i8f_array_ref);
            if constexpr (std::is_same_v<InputCT, float>) {
                quantizer.quantize(input_cells, u8_array_ref, quantization_mode);
            } else {
                convert_to_f32(input_cells, scratch_space);
                quantizer.quantize(scratch_space, u8_array_ref, quantization_mode);
            }
        } else {
            auto view = idx.create_view({});
            view->lookup({});
            std::vector<vespalib::string_id*> addr_fetch;
            addr_fetch.reserve(num_mapped);
            for (auto& label : addr) {
                addr_fetch.emplace_back(&label);
            }
            size_t subspace_idx;
            while (view->next_result(addr_fetch, subspace_idx)) {
                auto i8f_array_ref = builder->add_subspace(addr);
                auto u8_array_ref = span_cast<uint8_t>(i8f_array_ref);
                auto input = input_cells.subspan(input_dense_size * subspace_idx, input_dense_size);
                if constexpr (std::is_same_v<InputCT, float>) {
                    quantizer.quantize(input, u8_array_ref, quantization_mode);
                } else {
                    convert_to_f32(input, scratch_space);
                    quantizer.quantize(scratch_space, u8_array_ref, quantization_mode);
                }
            }
        }
        return builder->build(std::move(builder));
    }
};

struct DequantizeTensorWithImplicitOutputConversion {
    template <typename OutputCT>
    static std::unique_ptr<vespalib::eval::Value>
    invoke(const vespalib::eval::Value& quantized_in_tensor, const vespalib::eval::ValueType& out_tensor_type,
           vespalib::quant::EdenQuantizer& quantizer, std::vector<float>& scratch_space) {
        // We expect that the number of mapped dimensions is the same for both tensor types
        const size_t num_mapped = out_tensor_type.count_mapped_dimensions();
        const size_t quantized_dense_size = quantizer.quantized_size();
        const size_t dense_size = out_tensor_type.dense_subspace_size();
        const auto&  idx = quantized_in_tensor.index();
        auto         input_cells = quantized_in_tensor.cells().typify<Int8Float>();

        assert(dense_size == quantizer.dimensions());
        auto builder = FastValueBuilderFactory::get().create_value_builder<OutputCT>(out_tensor_type, num_mapped,
                                                                                     dense_size, idx.size());
        if constexpr (!std::is_same_v<OutputCT, float>) {
            scratch_space.resize(dense_size);
        }
        std::vector<vespalib::string_id> addr(num_mapped);
        if (num_mapped == 0) {
            assert(idx.size() == 1);
            auto array_ref = builder->add_subspace(addr);
            auto input_as_u8 = span_cast<const uint8_t>(input_cells);
            if constexpr (std::is_same_v<OutputCT, float>) {
                quantizer.dequantize(input_as_u8, array_ref);
            } else {
                quantizer.dequantize(input_as_u8, scratch_space);
                convert_from_f32(scratch_space, array_ref);
            }
        } else {
            auto view = idx.create_view({});
            view->lookup({});
            std::vector<vespalib::string_id*> addr_fetch;
            addr_fetch.reserve(num_mapped);
            for (auto& label : addr) {
                addr_fetch.emplace_back(&label);
            }
            size_t subspace_idx;
            while (view->next_result(addr_fetch, subspace_idx)) {
                auto array_ref = builder->add_subspace(addr);
                auto input = input_cells.subspan(quantized_dense_size * subspace_idx, quantized_dense_size);
                auto input_as_u8 = span_cast<const uint8_t>(input);
                if constexpr (std::is_same_v<OutputCT, float>) {
                    quantizer.dequantize(input_as_u8, array_ref);
                } else {
                    quantizer.dequantize(input_as_u8, scratch_space);
                    convert_from_f32(scratch_space, array_ref);
                }
            }
        }
        return builder->build(std::move(builder));
    }
};

} // namespace

std::unique_ptr<vespalib::eval::Value> quantize_tensor(const vespalib::eval::Value&     in_tensor,
                                                       const vespalib::eval::ValueType& quantized_type,
                                                       vespalib::quant::EdenQuantizer&  quantizer,
                                                       vespalib::quant::QuantMode       quantization_mode,
                                                       std::vector<float>&              scratch_space) {
    return vespalib::typify_invoke<1, TypifyCellType, QuantizeTensorWithImplicitInputConversion>(
        in_tensor.type().cell_type(), in_tensor, quantized_type, quantizer, quantization_mode, scratch_space);
}

std::unique_ptr<vespalib::eval::Value> dequantize_tensor(const vespalib::eval::Value&     quantized_in_tensor,
                                                         const vespalib::eval::ValueType& out_tensor_type,
                                                         vespalib::quant::EdenQuantizer&  quantizer,
                                                         std::vector<float>&              scratch_space) {
    return vespalib::typify_invoke<1, TypifyCellType, DequantizeTensorWithImplicitOutputConversion>(
        out_tensor_type.cell_type(), quantized_in_tensor, out_tensor_type, quantizer, scratch_space);
}

} // namespace search::tensor
