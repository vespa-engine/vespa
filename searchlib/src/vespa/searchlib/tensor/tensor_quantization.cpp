// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_quantization.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/quant/eden.h>

#include <cassert>
#include <type_traits>

using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::Int8Float;
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

} // namespace

// TODO consider if we should just pass the number of bits rather than a full quantizer...
//  - could make size computation a static public function in EdenQuantizer

ValueType to_quantized_tensor_type(const ValueType&                      f32_tensor_type,
                                   const vespalib::quant::EdenQuantizer& quantizer) {
    std::vector<ValueType::Dimension> q_dims = f32_tensor_type.mapped_dimensions();
    assert(!f32_tensor_type.is_sparse()); // Must have at least 1 indexed dimension
    assert(f32_tensor_type.dense_subspace_size() == quantizer.dimensions());
    // Leave all sparse dimensions untouched and replace all indexed dimensions with a single
    // quantized "aggregate" dimension. We cheekily reuse the name of the last indexed dimension
    // to ensure we use a name that is unique within the tensor type.
    q_dims.emplace_back(f32_tensor_type.indexed_dimensions().back().name, quantizer.quantized_size());
    return ValueType::make_type(CellType::INT8, std::move(q_dims));
}

// These functions have been cooked on firewood stol- uh, borrowed, from vespalib::eval::CopyValue. Yes. yes.

std::unique_ptr<vespalib::eval::Value> quantize_f32_tensor(const vespalib::eval::Value&     f32_in_tensor,
                                                           const vespalib::eval::ValueType& quantized_type,
                                                           vespalib::quant::EdenQuantizer&  quantizer,
                                                           const vespalib::quant::QuantMode quantization_mode) {
    // We expect that the number of mapped dimensions is the same for both tensor types
    const size_t num_mapped = quantized_type.count_mapped_dimensions();
    const size_t input_dense_size = f32_in_tensor.type().dense_subspace_size();
    const size_t quantized_dense_size = quantized_type.dense_subspace_size();
    const auto&  idx = f32_in_tensor.index();
    auto         input_cells = f32_in_tensor.cells().typify<float>();
    assert(input_dense_size == quantizer.dimensions());
    assert(quantized_dense_size == quantizer.quantized_size());
    assert(num_mapped == quantized_type.dimensions().size() - 1); // Indexed must be reduced down to 1 dimension
    auto builder = FastValueBuilderFactory::get().create_value_builder<Int8Float>(quantized_type, num_mapped,
                                                                                  quantized_dense_size, idx.size());
    std::vector<vespalib::string_id> addr(num_mapped);
    if (num_mapped == 0) {
        assert(idx.size() == 1);
        auto i8f_array_ref = builder->add_subspace(addr);
        auto u8_array_ref = span_cast<uint8_t>(i8f_array_ref);
        quantizer.quantize(input_cells, u8_array_ref, quantization_mode);
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
            quantizer.quantize(input, u8_array_ref, quantization_mode);
        }
    }
    return builder->build(std::move(builder));
}

std::unique_ptr<vespalib::eval::Value> dequantize_tensor(const vespalib::eval::Value&     quantized_in_tensor,
                                                         const vespalib::eval::ValueType& out_f32_tensor_type,
                                                         vespalib::quant::EdenQuantizer&  quantizer) {
    // We expect that the number of mapped dimensions is the same for both tensor types
    const size_t num_mapped = out_f32_tensor_type.count_mapped_dimensions();
    const size_t quantized_dense_size = quantizer.quantized_size();
    const size_t dense_size = out_f32_tensor_type.dense_subspace_size();
    const auto&  idx = quantized_in_tensor.index();
    auto         input_cells = quantized_in_tensor.cells().typify<Int8Float>();
    assert(dense_size == quantizer.dimensions());
    auto builder = FastValueBuilderFactory::get().create_value_builder<float>(out_f32_tensor_type, num_mapped,
                                                                              dense_size, idx.size());
    std::vector<vespalib::string_id> addr(num_mapped);
    if (num_mapped == 0) {
        assert(idx.size() == 1);
        auto f32_array_ref = builder->add_subspace(addr);
        auto input_as_u8 = span_cast<const uint8_t>(input_cells);
        quantizer.dequantize(input_as_u8, f32_array_ref);
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
            auto f32_array_ref = builder->add_subspace(addr);
            auto input = input_cells.subspan(quantized_dense_size * subspace_idx, quantized_dense_size);
            auto input_as_u8 = span_cast<const uint8_t>(input);
            quantizer.dequantize(input_as_u8, f32_array_ref);
        }
    }
    return builder->build(std::move(builder));
}

} // namespace search::tensor
