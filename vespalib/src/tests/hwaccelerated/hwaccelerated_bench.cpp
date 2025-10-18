// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <benchmark/benchmark.h>
#include <format>
#include <type_traits>

using namespace vespalib;
using namespace vespalib::hwaccelerated;

template <typename> constexpr bool type_dependent_false_v = false;

template <typename T>
constexpr std::string_view type_string() noexcept {
    if constexpr (std::is_same_v<T, int8_t>) {
        return "int8";
    } else if constexpr (std::is_same_v<T, uint8_t>) {
        return "uint8";
    } else if constexpr (std::is_same_v<T, int16_t>) {
        return "int16";
    } else if constexpr (std::is_same_v<T, int32_t>) {
        return "int32";
    } else if constexpr (std::is_same_v<T, BFloat16>) {
        return "BFloat16";
    } else if constexpr (std::is_same_v<T, float>) {
        return "float";
    } else if constexpr (std::is_same_v<T, double>) {
        return "double";
    } else if constexpr (std::is_same_v<T, uint64_t>) {
        return "uint64";
    } else {
        static_assert(type_dependent_false_v<T>, "type not known for stringification");
        return "";
    }
}

template <typename T, typename Fn>
void register_accel_binary_arg_benchmark(std::string_view name, std::unique_ptr<IAccelerated> accel, Fn&& fn) {
    std::string instance_name = std::format("{}/{}/{}/{}", name, type_string<T>(), accel->implementation_name(), accel->target_name());
    auto bench_fn = [fn = std::forward<Fn>(fn), accel = std::move(accel)](benchmark::State& state) {
        auto [a, b] = create_and_fill_lhs_rhs<T>(state.range());
        for (auto _ : state) {
            // A tiny bit dirty, but beats duplicating a bunch of code.
            constexpr bool is_void_fn = std::is_same_v<void, decltype(fn(*accel, a.data(), b.data(), state.range()))>;
            if constexpr (is_void_fn) {
                // Note that this is expected to mutate `a`, so different iterations do not
                // necessarily use the same input data. Should not be not an issue in practice(tm).
                fn(*accel, a.data(), b.data(), state.range());
                // _Technically_ the compiler could stare into the void and realize the above
                // code has no side effects since the output is not used for anything. Avoid this
                // by having the void stare back (clobber output memory with an opaque access).
                auto* clobber_data = a.data(); // Non-const ptr; pretend we may read/write
                benchmark::DoNotOptimize(clobber_data);
                benchmark::ClobberMemory(); // Compiler write barrier
            } else {
                auto result = fn(*accel, a.data(), b.data(), state.range());
                benchmark::DoNotOptimize(result);
            }
        }
        state.SetBytesProcessed(sizeof(T) * state.range() * state.iterations() * 2); // *2 due to lhs+rhs
    };
    auto* bench = benchmark::RegisterBenchmark(instance_name, std::move(bench_fn));
    bench->RangeMultiplier(2)->Range(8, 8<<10); // TODO also with non-aligned sizes
}

template <typename T, typename Fn>
void register_benchmarks(std::string_view name, Fn fn) {
    auto hwy_targets = Highway::create_supported_targets();
    for (auto& t : hwy_targets) {
        register_accel_binary_arg_benchmark<T>(name, std::move(t), fn);
    }
    auto baseline_accel = IAccelerated::create_baseline_auto_vectorized_target();
    register_accel_binary_arg_benchmark<T>(name, std::move(baseline_accel), fn);

    auto best_autovec_accel = IAccelerated::create_best_auto_vectorized_target();
    register_accel_binary_arg_benchmark<T>(name, std::move(best_autovec_accel), fn);
}

void register_all_benchmark_suites() {
    auto euclidean_dist_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.squaredEuclideanDistance(lhs, rhs, my_sz);
    };
    register_benchmarks<double>("Squared Euclidean Distance", euclidean_dist_fn);
    register_benchmarks<float>("Squared Euclidean Distance", euclidean_dist_fn);
    register_benchmarks<BFloat16>("Squared Euclidean Distance", euclidean_dist_fn);
    register_benchmarks<int8_t>("Squared Euclidean Distance", euclidean_dist_fn);

    auto dot_product_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.dotProduct(lhs, rhs, my_sz);
    };
    register_benchmarks<double>("Dot Product", dot_product_fn);
    register_benchmarks<float>("Dot Product", dot_product_fn);
    register_benchmarks<BFloat16>("Dot Product", dot_product_fn);
    register_benchmarks<int8_t>("Dot Product", dot_product_fn);

    auto binary_hamming_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.binary_hamming_distance(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Binary Hamming Distance", binary_hamming_fn);

    auto popcount_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky; overestimates bytes processed/sec by 2x
        return accelerator.populationCount(lhs, my_sz);
    };
    register_benchmarks<uint64_t>("Popcount", popcount_fn);

    auto and_fn = [](const IAccelerated& accel, auto* lhs, const auto* rhs, size_t my_sz) {
        accel.andBit(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Bitwise AND", and_fn);

    auto or_fn = [](const IAccelerated& accel, auto* lhs, const auto* rhs, size_t my_sz) {
        return accel.orBit(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Bitwise OR", or_fn);

    auto and_not_fn = [](const IAccelerated& accel, auto* lhs, const auto* rhs, size_t my_sz) {
        return accel.andNotBit(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Bitwise ANDNOT", and_not_fn);
}

int main(int argc, char *argv[]) {
    benchmark::MaybeReenterWithoutASLR(argc, argv);

    register_all_benchmark_suites();

    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
