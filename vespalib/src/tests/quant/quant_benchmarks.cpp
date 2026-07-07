// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/quant/eden.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <benchmark/benchmark.h>

#include <random>
#include <ranges>

namespace vespalib::quant {

namespace {

// Fills every entry in `v` with a random float in [-1, 1]. No normalization is done.
template <std::uniform_random_bit_generator Rng>
void fill_random(std::span<float> v, Rng& rng) noexcept {
    std::uniform_real_distribution<float> dist(-1.f, 1.f);
    std::ranges::generate(v, [&]() mutable { return dist(rng); });
}

// Not thread safe
[[nodiscard]] uint64_t gen_true_random() noexcept {
    static std::random_device rng;
    return rng();
}

} // namespace

// Vector quantization with args <bits, quant mode, dimensions>
template <typename... Args>
void BM_quantize(benchmark::State& state, Args&&... args) {
    const auto      my_args = std::make_tuple(std::move(args)...);
    const uint8_t   bits = std::get<0>(my_args);
    const QuantMode mode = std::get<1>(my_args);
    const size_t    dims = state.range();

    Xoshiro256PlusPlusPrng prng(gen_true_random());
    EdenQuantizer          quant(dims, bits, prng());
    std::vector<float>     v(dims);
    std::vector<uint8_t>   q_v(quant.quantized_size());
    fill_random(v, prng);

    for (auto _ : state) {
        // We assume that the compiler is not clever enough to understand that this
        // computes the same result every time...
        quant.quantize(v, MutableQuantizedVector(q_v), mode);
        benchmark::ClobberMemory();
        auto* clobber_data = q_v.data();
        benchmark::DoNotOptimize(clobber_data);
    }
}

// Vector dequantization with args <bits, dimensions>
template <typename... Args>
void BM_dequantize(benchmark::State& state, Args&&... args) {
    const auto    my_args = std::make_tuple(std::move(args)...);
    const uint8_t bits = std::get<0>(my_args);
    const size_t  dims = state.range();

    Xoshiro256PlusPlusPrng prng(gen_true_random());
    EdenQuantizer          quant(dims, bits, prng());
    std::vector<float>     v(dims);
    std::vector<uint8_t>   q_v(quant.quantized_size());
    fill_random(v, prng);

    // The quantization mode doesn't matter for dequantization performance, so just use MSE
    quant.quantize(v, MutableQuantizedVector(q_v), QuantMode::MSE);
    std::vector<float> dq_v(dims);

    for (auto _ : state) {
        quant.dequantize(QuantizedVector(q_v), dq_v);
        benchmark::ClobberMemory();
        auto* clobber_data = dq_v.data();
        benchmark::DoNotOptimize(clobber_data);
    }
}

// Pre-rotated full precision vector vs. quantized vector dot product with args <bits, dimensions>
template <typename... Args>
void BM_pre_rotated_dot_product(benchmark::State& state, Args&&... args) {
    const auto    my_args = std::make_tuple(std::move(args)...);
    const uint8_t bits = std::get<0>(my_args);
    const size_t  dims = state.range();

    Xoshiro256PlusPlusPrng prng(gen_true_random());
    EdenQuantizer          quant(dims, bits, prng());
    std::vector<float>     v(dims);
    std::vector<uint8_t>   q_v(quant.quantized_size());
    fill_random(v, prng);

    quant.quantize(v, MutableQuantizedVector(q_v), QuantMode::InnerProduct);
    quant.rotate_vector_inplace(v);

    for (auto _ : state) {
        float dot = quant.pre_rotated_query_dot_product(v, QuantizedVector(q_v));
        benchmark::DoNotOptimize(dot);
    }
}

// Pre-rotated full precision vector vs. quantized vector squared Euclidean distance with args <bits, dimensions>
template <typename... Args>
void BM_pre_rotated_squared_euclidean_distance(benchmark::State& state, Args&&... args) {
    const auto    my_args = std::make_tuple(std::move(args)...);
    const uint8_t bits = std::get<0>(my_args);
    const size_t  dims = state.range();

    Xoshiro256PlusPlusPrng prng(gen_true_random());
    EdenQuantizer          quant(dims, bits, prng());
    std::vector<float>     v(dims);
    std::vector<uint8_t>   q_v(quant.quantized_size());
    fill_random(v, prng);

    quant.quantize(v, MutableQuantizedVector(q_v), QuantMode::MSE);
    quant.rotate_vector_inplace(v);

    for (auto _ : state) {
        float dist = quant.pre_rotated_query_squared_euclidean_distance(v, QuantizedVector(q_v));
        benchmark::DoNotOptimize(dist);
    }
}

// TODO benchmark non-power of 2 sizes

BENCHMARK_CAPTURE(BM_quantize, 1_bit_mse, 1, QuantMode::MSE)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_quantize, 2_bits_mse, 2, QuantMode::MSE)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_quantize, 3_bits_mse, 3, QuantMode::MSE)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_quantize, 4_bits_mse, 4, QuantMode::MSE)->Range(8, 8192);

BENCHMARK_CAPTURE(BM_quantize, 1_bit_inner_product, 1, QuantMode::InnerProduct)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_quantize, 2_bits_inner_product, 2, QuantMode::InnerProduct)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_quantize, 3_bits_inner_product, 3, QuantMode::InnerProduct)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_quantize, 4_bits_inner_product, 4, QuantMode::InnerProduct)->Range(8, 8192);

BENCHMARK_CAPTURE(BM_dequantize, 1_bit, 1)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_dequantize, 2_bits, 2)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_dequantize, 3_bits, 3)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_dequantize, 4_bits, 4)->Range(8, 8192);

BENCHMARK_CAPTURE(BM_pre_rotated_dot_product, 1_bit, 1)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_pre_rotated_dot_product, 2_bits, 2)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_pre_rotated_dot_product, 3_bits, 3)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_pre_rotated_dot_product, 4_bits, 4)->Range(8, 8192);

BENCHMARK_CAPTURE(BM_pre_rotated_squared_euclidean_distance, 1_bit, 1)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_pre_rotated_squared_euclidean_distance, 2_bits, 2)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_pre_rotated_squared_euclidean_distance, 3_bits, 3)->Range(8, 8192);
BENCHMARK_CAPTURE(BM_pre_rotated_squared_euclidean_distance, 4_bits, 4)->Range(8, 8192);

} // namespace vespalib::quant

BENCHMARK_MAIN();
