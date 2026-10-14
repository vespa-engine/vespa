// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include "scoped_fn_table_override.h"

#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>

#include <benchmark/benchmark.h>

#include <format>
#include <type_traits>

using namespace vespalib;
using namespace vespalib::hwaccelerated;
using dispatch::FnTable;

template <typename> constexpr bool type_dependent_false_v = false;

template <typename T> constexpr std::string_view type_string() noexcept {
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

template <typename T, typename MakeInputFn, typename KernelFn>
void register_accel_binary_arg_benchmark(const std::string& instance_name, std::unique_ptr<IAccelerated> accel,
                                         MakeInputFn&& make_input_fn, KernelFn&& kernel_fn) {
    auto bench_fn = [kernel_fn = std::forward<KernelFn>(kernel_fn),
                     make_input_fn = std::forward<MakeInputFn>(make_input_fn),
                     accel = std::move(accel)](benchmark::State& state) {
        auto [a, b] = make_input_fn(state.range());
        ScopedFnTableOverride fn_scope(accel->fn_table());
        for (auto _ : state) {
            // A tiny bit dirty, but beats duplicating a bunch of code.
            constexpr bool is_void_fn = std::is_same_v<void, decltype(kernel_fn(a.data(), b.data(), state.range()))>;
            if constexpr (is_void_fn) {
                // Note that this is expected to mutate `a`, so different iterations do not
                // necessarily use the same input data. Should not be not an issue in practice(tm).
                kernel_fn(a.data(), b.data(), state.range());
                // _Technically_ the compiler could stare into the void and realize the above
                // code has no side effects since the output is not used for anything. Avoid this
                // by having the void stare back (clobber output memory with an opaque access).
                auto* clobber_data_a = a.data(); // Non-const ptrs; pretend we may read/write
                benchmark::DoNotOptimize(clobber_data_a);
                auto* clobber_data_b = b.data();
                benchmark::DoNotOptimize(clobber_data_b);
                benchmark::ClobberMemory(); // Compiler write barrier
            } else {
                auto result = kernel_fn(a.data(), b.data(), state.range());
                benchmark::DoNotOptimize(result);
            }
        }
        state.SetBytesProcessed(sizeof(T) * state.range() * state.iterations() * 2); // *2 due to lhs+rhs
    };
    auto* bench = benchmark::RegisterBenchmark(instance_name, std::move(bench_fn));
    bench->RangeMultiplier(2)->Range(8, 8 << 10); // TODO also with non-aligned sizes
}

template <typename BenchFn, typename RegisterFn> void
register_benchmark_for_all_accels(FnTable::FnId fn_id, BenchFn bench_fn, RegisterFn register_fn) {
    auto hwy_targets = Highway::create_supported_targets();
    for (auto& t : hwy_targets) {
        if (t->fn_table().has_fn(fn_id)) {
            register_fn(std::move(t), bench_fn);
        }
    }
    auto auto_vec_targets = IAccelerated::create_supported_auto_vectorized_targets();
    for (auto& t : auto_vec_targets) {
        if (t->fn_table().has_fn(fn_id)) {
            register_fn(std::move(t), bench_fn);
        }
    }
}

template <typename T>
[[nodiscard]] std::string make_bench_instance_name(std::string_view base_name, const TargetInfo& accel_target) {
    return std::format("{}/{}/{}/{}", base_name, type_string<T>(), accel_target.implementation_name(),
                       accel_target.target_name());
}

template <typename T, typename Fn>
void register_benchmarks(std::string_view name, FnTable::FnId fn_id, Fn fn) {
    register_benchmark_for_all_accels(fn_id, fn, [name](auto accel, auto bench_fn) {
        std::string instance_name = make_bench_instance_name<T>(name, accel->target_info());
        auto        input_fn = [](const size_t sz) { return create_and_fill_lhs_rhs<T>(sz); };
        register_accel_binary_arg_benchmark<T>(instance_name, std::move(accel), input_fn, bench_fn);
    });
}

template <typename T>
void register_unpack_bits_benchmark(FnTable::FnId fn_id) {
    auto input_fn = [](const size_t sz) {
        Xoshiro256PlusPlusPrng prng(1234567);
        std::vector<int8_t> src = create_and_fill<int8_t>(prng, sz); // lhs
        std::vector<T>      dest(sz * 8);                            // rhs; 1 element per input i8 element bit
        return std::make_pair(std::move(src), std::move(dest));
    };
    auto unpack_bits_little_fn = [](const int8_t* src, auto* dest, const size_t sz) noexcept {
        return unpack_bits(src, dest, sz, false);
    };
    register_benchmark_for_all_accels(fn_id, unpack_bits_little_fn, [input_fn](auto accel, auto bench_fn) {
        std::string instance_name = make_bench_instance_name<T>("Unpack Bits LE", accel->target_info());
        register_accel_binary_arg_benchmark<T>(instance_name, std::move(accel), input_fn, bench_fn);
    });
    auto unpack_bits_big_fn = [](const int8_t* src, auto* dest, const size_t sz) noexcept {
        return unpack_bits(src, dest, sz, true);
    };
    register_benchmark_for_all_accels(fn_id, unpack_bits_big_fn, [input_fn](auto accel, auto bench_fn) {
        std::string instance_name = make_bench_instance_name<T>("Unpack Bits BE", accel->target_info());
        register_accel_binary_arg_benchmark<T>(instance_name, std::move(accel), input_fn, bench_fn);
    });
}

void register_all_benchmark_suites() {
    auto euclidean_dist_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return squared_euclidean_distance(lhs, rhs, my_sz);
    };
    register_benchmarks<double>("Squared Euclidean Distance", FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F64,
                                euclidean_dist_fn);
    register_benchmarks<float>("Squared Euclidean Distance", FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F32,
                               euclidean_dist_fn);
    register_benchmarks<BFloat16>("Squared Euclidean Distance", FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_BF16,
                                  euclidean_dist_fn);
    register_benchmarks<int8_t>("Squared Euclidean Distance", FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_I8,
                                euclidean_dist_fn);

    auto euclidean_length_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky; overestimates bytes processed/sec by 2x
        return squared_euclidean_length(lhs, my_sz);
    };
    register_benchmarks<float>("Squared Euclidean Length", FnTable::FnId::SQUARED_EUCLIDEAN_LENGTH_F32,
                               euclidean_length_fn);

    auto dot_product_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) { return dot_product(lhs, rhs, my_sz); };
    register_benchmarks<double>("Dot Product", FnTable::FnId::DOT_PRODUCT_F64, dot_product_fn);
    register_benchmarks<float>("Dot Product", FnTable::FnId::DOT_PRODUCT_F32, dot_product_fn);
    register_benchmarks<BFloat16>("Dot Product", FnTable::FnId::DOT_PRODUCT_BF16, dot_product_fn);
    register_benchmarks<int8_t>("Dot Product", FnTable::FnId::DOT_PRODUCT_I8, dot_product_fn);

    auto binary_hamming_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        return binary_hamming_distance(lhs, rhs, my_sz);
    };
    register_benchmarks<uint8_t>("Binary Hamming Distance", FnTable::FnId::BINARY_HAMMING_DISTANCE,
                                 binary_hamming_fn);

    auto popcount_fn = [](const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky; overestimates bytes processed/sec by 2x
        return population_count(lhs, my_sz);
    };
    register_benchmarks<uint64_t>("Popcount", FnTable::FnId::POPULATION_COUNT, popcount_fn);

    auto and_fn = [](auto* lhs, const auto* rhs, size_t my_sz) { and_bit(lhs, rhs, my_sz); };
    register_benchmarks<uint8_t>("Bitwise AND", FnTable::FnId::AND_BIT, and_fn);

    auto or_fn = [](auto* lhs, const auto* rhs, size_t my_sz) { return or_bit(lhs, rhs, my_sz); };
    register_benchmarks<uint8_t>("Bitwise OR", FnTable::FnId::OR_BIT, or_fn);

    auto and_not_fn = [](auto* lhs, const auto* rhs, size_t my_sz) { return and_not_bit(lhs, rhs, my_sz); };
    register_benchmarks<uint8_t>("Bitwise ANDNOT", FnTable::FnId::AND_NOT_BIT, and_not_fn);

    register_unpack_bits_benchmark<int8_t>(FnTable::FnId::UNPACK_BITS_TO_8X_I8);
    register_unpack_bits_benchmark<BFloat16>(FnTable::FnId::UNPACK_BITS_TO_8X_BF16);
    register_unpack_bits_benchmark<float>(FnTable::FnId::UNPACK_BITS_TO_8X_F32);
    register_unpack_bits_benchmark<double>(FnTable::FnId::UNPACK_BITS_TO_8X_F64);
}

int main(int argc, char* argv[]) {
    benchmark::MaybeReenterWithoutASLR(argc, argv);

    register_all_benchmark_suites();

    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
