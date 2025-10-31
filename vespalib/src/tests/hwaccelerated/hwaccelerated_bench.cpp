// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include "scoped_fn_table_override.h"
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/hwaccelerated/float4.h>
#include <vespa/vespalib/hwaccelerated/float8.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <benchmark/benchmark.h>
#include <format>
#include <type_traits>

using namespace vespalib;
using namespace vespalib::hwaccelerated;
using dispatch::FnTable;

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
    } else if constexpr (std::is_same_v<T, Float8E4M3FN>) {
        return "Float8E4M3FN";
    } else if constexpr (std::is_same_v<T, Float8E5M2>) {
        return "Float8E5M2";
    } else if constexpr (std::is_same_v<T, Float4E2M1_X2>) {
        return "Float4E2M1_X2";
    } else {
        static_assert(type_dependent_false_v<T>, "type not known for stringification");
        return "";
    }
}

template <typename T> struct NativeFromT { using type = T; };
template <> struct NativeFromT<Float8E4M3FN> { using type = uint8_t; };
template <> struct NativeFromT<Float8E5M2> { using type = uint8_t; };
template <> struct NativeFromT<Float4E2M1_X2> { using type = uint8_t; };

template <typename> struct IsFinite {
    template <typename T2>
    constexpr bool operator()(T2) const noexcept { return true; }
};
template <> struct IsFinite<Float8E4M3FN> {
    constexpr bool operator()(uint8_t v) const noexcept { return Float8E4M3FN::is_finite(v); }
};
template <> struct IsFinite<Float8E5M2> {
    constexpr bool operator()(uint8_t v) const noexcept { return Float8E5M2::is_finite(v); }
};

template <typename T, typename Fn>
void register_accel_binary_arg_benchmark(std::string_view name, std::unique_ptr<IAccelerated> accel, Fn&& fn) {
    const auto accel_target = accel->target_info();
    std::string instance_name = std::format("{}/{}/{}/{}", name, type_string<T>(), accel_target.implementation_name(), accel_target.target_name());
    auto bench_fn = [fn = std::forward<Fn>(fn), accel = std::move(accel)](benchmark::State& state) {
        auto [a, b] = create_and_fill_lhs_rhs<typename NativeFromT<T>::type>(state.range(), IsFinite<T>{});
        ScopedFnTableOverride fn_scope(accel->fn_table());
        for (auto _ : state) {
            // A tiny bit dirty, but beats duplicating a bunch of code.
            constexpr bool is_void_fn = std::is_same_v<void, decltype(fn(a.data(), b.data(), state.range()))>;
            if constexpr (is_void_fn) {
                // Note that this is expected to mutate `a`, so different iterations do not
                // necessarily use the same input data. Should not be not an issue in practice(tm).
                fn(a.data(), b.data(), state.range());
                // _Technically_ the compiler could stare into the void and realize the above
                // code has no side effects since the output is not used for anything. Avoid this
                // by having the void stare back (clobber output memory with an opaque access).
                auto* clobber_data = a.data(); // Non-const ptr; pretend we may read/write
                benchmark::DoNotOptimize(clobber_data);
                benchmark::ClobberMemory(); // Compiler write barrier
            } else {
                auto result = fn(a.data(), b.data(), state.range());
                benchmark::DoNotOptimize(result);
            }
        }
        state.SetBytesProcessed(sizeof(T) * state.range() * state.iterations() * 2); // *2 due to lhs+rhs
    };
    auto* bench = benchmark::RegisterBenchmark(instance_name, std::move(bench_fn));
    bench->RangeMultiplier(2)->Range(8, 8<<10); // TODO also with non-aligned sizes
}

template <typename T, typename Fn>
void register_benchmarks(std::string_view name, FnTable::FnId fn_id, Fn fn) {
    auto hwy_targets = Highway::create_supported_targets();
    for (auto& t : hwy_targets) {
        if (t->fn_table().has_fn(fn_id)) {
            register_accel_binary_arg_benchmark<T>(name, std::move(t), fn);
        }
    }
    auto auto_vec_targets = IAccelerated::create_supported_auto_vectorized_targets();
    for (auto& t : auto_vec_targets) {
        if (t->fn_table().has_fn(fn_id)) {
            register_accel_binary_arg_benchmark<T>(name, std::move(t), fn);
        }
    }
}

void register_all_benchmark_suites() {
    auto euclidean_dist_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return squared_euclidean_distance(lhs, rhs, my_sz);
    };
    register_benchmarks<double>("Squared Euclidean Distance",   FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F64,  euclidean_dist_fn);
    register_benchmarks<float>("Squared Euclidean Distance",    FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F32,  euclidean_dist_fn);
    register_benchmarks<BFloat16>("Squared Euclidean Distance", FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_BF16, euclidean_dist_fn);
    register_benchmarks<int8_t>("Squared Euclidean Distance",   FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_I8,   euclidean_dist_fn);

    auto dot_product_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return dot_product(lhs, rhs, my_sz);
    };
    register_benchmarks<double>("Dot Product",   FnTable::FnId::DOT_PRODUCT_F64,  dot_product_fn);
    register_benchmarks<float>("Dot Product",    FnTable::FnId::DOT_PRODUCT_F32,  dot_product_fn);
    register_benchmarks<BFloat16>("Dot Product", FnTable::FnId::DOT_PRODUCT_BF16, dot_product_fn);
    register_benchmarks<int8_t>("Dot Product",   FnTable::FnId::DOT_PRODUCT_I8,   dot_product_fn);

    auto dot_product_fp8_e4m3fn_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return dot_product(lhs, rhs, my_sz, Float8E4M3FN::kind());
    };
    register_benchmarks<Float8E4M3FN>("Dot Product", FnTable::FnId::DOT_PRODUCT_MICRO_FLOAT, dot_product_fp8_e4m3fn_fn);
    auto dot_product_fp8_e5m2_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return dot_product(lhs, rhs, my_sz, Float8E5M2::kind());
    };
    register_benchmarks<Float8E5M2>("Dot Product", FnTable::FnId::DOT_PRODUCT_MICRO_FLOAT, dot_product_fp8_e5m2_fn);
    auto dot_product_fp4_e2m1_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return dot_product(lhs, rhs, my_sz, Float4E2M1_X2::kind());
    };
    register_benchmarks<Float4E2M1_X2>("Dot Product", FnTable::FnId::DOT_PRODUCT_MICRO_FLOAT, dot_product_fp4_e2m1_fn);

    auto binary_hamming_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return binary_hamming_distance(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Binary Hamming Distance", FnTable::FnId::BINARY_HAMMING_DISTANCE, binary_hamming_fn);

    auto popcount_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky; overestimates bytes processed/sec by 2x
        return population_count(lhs, my_sz);
    };
    register_benchmarks<uint64_t>("Popcount", FnTable::FnId::POPULATION_COUNT, popcount_fn);

    auto and_fn = [](auto* lhs, const auto* rhs, size_t my_sz) {
        and_bit(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Bitwise AND", FnTable::FnId::AND_BIT, and_fn);

    auto or_fn = [](auto* lhs, const auto* rhs, size_t my_sz) {
        return or_bit(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Bitwise OR", FnTable::FnId::OR_BIT, or_fn);

    auto and_not_fn = [](auto* lhs, const auto* rhs, size_t my_sz) {
        return and_not_bit(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Bitwise ANDNOT", FnTable::FnId::AND_NOT_BIT, and_not_fn);
}

int main(int argc, char *argv[]) {
    benchmark::MaybeReenterWithoutASLR(argc, argv);

    register_all_benchmark_suites();

    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
