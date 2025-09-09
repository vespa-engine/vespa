// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <vespa/vespalib/util/time.h>
#include <benchmark/benchmark.h>
#include <format>
#include <type_traits>

using namespace vespalib;
using namespace vespalib::hwaccelerated;

template <typename T>
void do_not_optimize_away(T&& t) noexcept {
    asm volatile("" : : "m"(t) : "memory"); // Clobber the value to avoid losing it to compiler optimizations
}

template <typename T, typename Fn>
void benchmark_void_fn(Fn f, size_t sz, size_t n_iters) {
    auto [a, b] = create_and_fill_lhs_rhs<T>(sz);
    steady_time start = steady_clock::now();
    for (size_t i = 0; i < n_iters; ++i) {
        // Note that this is expected to mutate `a`, so different iterations do not
        // necessarily use the same input data. Should not be not an issue in practice(tm).
        f(a.data(), b.data(), sz);
    }
    if (n_iters > 0) {
        // _Technically_ the compiler could stare into the void and realize the above
        // code has no side effects since the output is not used for anything. So just
        // to be on the safe side, clobber the final output byte.
        do_not_optimize_away(a[a.size() - 1]);
    }
    duration elapsed = steady_clock::now() - start;
    printf("N=%zu and vector length=%zu took %.2f ms\n", n_iters, sz,
           std::chrono::duration<double, std::milli>(elapsed).count());
}

template <typename Fn>
void benchmark_byte_transform_fn(Fn fn, const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto wrapped_fn = [&accelerator, fn](auto* lhs, const auto* rhs, size_t my_sz) {
        fn(accelerator, lhs, rhs, my_sz);
    };
    printf("uint8_t : ");
    benchmark_void_fn<uint8_t>(wrapped_fn, sz, n_iters);
}

void benchmark_bitwise_and(const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto and_fn = [](const IAccelerated& accel, auto* lhs, const auto* rhs, size_t my_sz) {
        return accel.andBit(lhs, rhs, my_sz);
    };
    benchmark_byte_transform_fn(and_fn, accelerator, sz, n_iters);
}

void benchmark_bitwise_or(const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto or_fn = [](const IAccelerated& accel, auto* lhs, const auto* rhs, size_t my_sz) {
        return accel.orBit(lhs, rhs, my_sz);
    };
    benchmark_byte_transform_fn(or_fn, accelerator, sz, n_iters);
}

void benchmark_bitwise_and_not(const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto and_not_fn = [](const IAccelerated& accel, auto* lhs, const auto* rhs, size_t my_sz) {
        return accel.andNotBit(lhs, rhs, my_sz);
    };
    benchmark_byte_transform_fn(and_not_fn, accelerator, sz, n_iters);
}

void for_each_hwy_target(auto&& fn) {
    const auto hwy_targets = Highway::create_supported_targets();
    for (const auto& t : hwy_targets) {
        fn(*t);
    }
}

template <typename Fn>
void run_benchmark(Fn fn, const char* name, size_t sz, size_t n_iters) {
    auto baseline_accel      = IAccelerated::create_platform_baseline_accelerator();
    const auto& native_accel = IAccelerated::getAccelerator();

    printf("\n");
    for_each_hwy_target([&](const IAccelerated& hwy_accel) {
        printf("%s - Highway (%s)\n", name, hwy_accel.target_name());
        fn(hwy_accel, sz, n_iters);
    });
    printf("%s - Legacy baseline (%s)\n", name, baseline_accel->target_name());
    fn(*baseline_accel, sz, n_iters);
    printf("%s - Legacy optimized for this CPU (%s)\n", name, native_accel.target_name());
    fn(native_accel, sz, n_iters);
}

template <typename T0, typename T1, typename Fn>
void register_accel_binary_arg_benchmark(const std::string& name, std::unique_ptr<IAccelerated> accel, Fn fn) {
    auto bench_fn = [fn, accel = std::move(accel)](benchmark::State& state) {
        auto [a, b] = create_and_fill_lhs_rhs<T0, T1>(state.range(0));
        for (auto _ : state) {
            auto sum = fn(*accel, a.data(), b.data(), state.range(0));
            benchmark::DoNotOptimize(sum);
        }
        state.SetBytesProcessed((sizeof(T0) * state.range(0) * state.iterations()) +
                                (sizeof(T1) * state.range(0) * state.iterations()));
    };
    auto* bench = benchmark::RegisterBenchmark(name, std::move(bench_fn));
    bench->RangeMultiplier(2)->Range(8, 8<<10); // TODO also with non-aligned sizes
}

template <typename> constexpr bool type_dependent_false_v = false;

template <typename T>
constexpr std::string_view type_string() noexcept {
    if constexpr (std::is_same_v<T, int8_t>) {
        return "int8";
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

template <typename T0, typename T1, typename Fn>
void register_benchmarks(const std::string& name, Fn fn) {
    auto hwy_targets = Highway::create_supported_targets();
    for (auto& t : hwy_targets) {
        std::string hwy_name = std::format("{}/{}:{}/Highway/{}", name, type_string<T0>(), type_string<T1>(), t->target_name());
        register_accel_binary_arg_benchmark<T0, T1>(hwy_name, std::move(t), fn);
    }
    // TODO remove these in a Highway-by-default world:
    auto baseline_accel = IAccelerated::create_platform_baseline_accelerator();
    std::string baseline_name = std::format("{}/{}:{}/Legacy/{}", name, type_string<T0>(), type_string<T1>(), baseline_accel->target_name());
    register_accel_binary_arg_benchmark<T0, T1>(baseline_name, std::move(baseline_accel), fn);

    auto best_autovec_accel = IAccelerated::create_platform_optimal_accelerator();
    std::string autovec_name = std::format("{}/{}:{}/Legacy/{}", name, type_string<T0>(), type_string<T1>(), best_autovec_accel->target_name());
    register_accel_binary_arg_benchmark<T0, T1>(autovec_name, std::move(best_autovec_accel), fn);
}

int main(int argc, char *argv[]) {
    benchmark::MaybeReenterWithoutASLR(argc, argv);

    auto euclidean_dist_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.squaredEuclideanDistance(lhs, rhs, my_sz);
    };
    register_benchmarks<double, double>("Squared Euclidean Distance", euclidean_dist_fn);
    register_benchmarks<float, float>("Squared Euclidean Distance", euclidean_dist_fn);
    register_benchmarks<BFloat16, BFloat16>("Squared Euclidean Distance", euclidean_dist_fn);
    register_benchmarks<int8_t, int8_t>("Squared Euclidean Distance", euclidean_dist_fn);

    auto dot_product_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.dotProduct(lhs, rhs, my_sz);
    };
    register_benchmarks<double, double>("Dot Product", dot_product_fn);
    register_benchmarks<float, float>("Dot Product", dot_product_fn);
    register_benchmarks<BFloat16, BFloat16>("Dot Product", dot_product_fn);
    register_benchmarks<float, BFloat16>("Dot Product", dot_product_fn);
    register_benchmarks<int8_t, int8_t>("Dot Product", dot_product_fn);

    auto popcount_fn = [](const IAccelerated& accelerator, const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky
        return accelerator.populationCount(lhs, my_sz);
    };
    register_benchmarks<uint64_t, uint64_t>("Popcount", popcount_fn);

    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
