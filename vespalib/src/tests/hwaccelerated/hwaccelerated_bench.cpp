// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <vespa/vespalib/util/time.h>
#include <cinttypes>

using namespace vespalib;
using namespace vespalib::hwaccelerated;

template <typename T>
void do_not_optimize_away(T&& t) noexcept {
    asm volatile("" : : "m"(t) : "memory"); // Clobber the value to avoid losing it to compiler optimizations
}

template <typename T, typename Fn>
void benchmark_fn(Fn f, size_t sz, size_t n_iters) {
    auto [a, b] = create_and_fill_lhs_rhs<T>(sz);
    steady_time start = steady_clock::now();
    double sumOfSums(0);
    for (size_t j(0); j < n_iters; j++) {
        double sum = f(a.data(), b.data(), sz);
        sumOfSums += sum;
    }
    duration elapsed = steady_clock::now() - start;
    printf("sum=%f of N=%zu and vector length=%zu took %.2f ms\n", sumOfSums, n_iters, sz,
           std::chrono::duration<double, std::milli>(elapsed).count());
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

void benchmark_squared_euclidean_distance(const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto euclidean_dist_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.squaredEuclideanDistance(lhs, rhs, my_sz);
    };
    printf("double : ");
    benchmark_fn<double>(euclidean_dist_fn, sz, n_iters);
    printf("float  : ");
    benchmark_fn<float>(euclidean_dist_fn, sz, n_iters);
    printf("BF16   : ");
    benchmark_fn<BFloat16>(euclidean_dist_fn, sz, n_iters);
    printf("int8_t : ");
    benchmark_fn<int8_t>(euclidean_dist_fn, sz, n_iters);
}

void benchmark_dot_product(const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto dot_product_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.dotProduct(lhs, rhs, my_sz);
    };
    printf("double : ");
    benchmark_fn<double>(dot_product_fn, sz, n_iters);
    printf("float  : ");
    benchmark_fn<float>(dot_product_fn, sz, n_iters);
    printf("BF16   : ");
    benchmark_fn<BFloat16>(dot_product_fn, sz, n_iters);
    printf("int8_t : ");
    benchmark_fn<int8_t>(dot_product_fn, sz, n_iters);
}

void benchmark_popcount(const IAccelerated& accelerator, size_t sz, size_t n_iters) {
    auto popcount_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky
        return accelerator.populationCount(lhs, my_sz);
    };
    printf("uint64_t : ");
    benchmark_fn<uint64_t>(popcount_fn, sz, n_iters);
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

void perform_initial_warmup(size_t sz, size_t n_iters) {
    const auto& native_accel = IAccelerated::getAccelerator();
    // Run a single warmup run to crank up the CPU power budget enough that any downclocking
    // should be immediately visible. Use the widest ("most optimal") available vectors (e.g.
    // AVX-512 on x64) for this, since it's the most susceptible to throttling.
    // So the term "warmup" in this case is fairly literal.
    printf("Squared Euclidean Distance - Warmup round (%s)\n", native_accel.target_name());
    benchmark_squared_euclidean_distance(native_accel, sz, n_iters);
    printf("--------\n");
}

int main(int argc, char *argv[]) {
    int length = 1000;
    int n_iters = 1000000;
    if (argc > 1) {
        length = atol(argv[1]);
    }
    if (argc > 2) {
        n_iters = atol(argv[2]);
    }

    printf("%s %d %d\n", argv[0], length, n_iters);
    perform_initial_warmup(length, n_iters);

    run_benchmark(benchmark_squared_euclidean_distance, "Squared Euclidean Distance", length, n_iters);
    run_benchmark(benchmark_dot_product, "Dot Product", length, n_iters);
    run_benchmark(benchmark_popcount, "Popcount", length, n_iters);
    // For bitwise ops, implicitly increase the length since they are the cheapest
    // possible ops and also operate on byte vectors.
    size_t bitwise_length = length * 10;
    run_benchmark(benchmark_bitwise_and, "Bitwise AND", bitwise_length, n_iters);
    run_benchmark(benchmark_bitwise_or, "Bitwise OR", bitwise_length, n_iters);
    run_benchmark(benchmark_bitwise_and_not, "Bitwise AND NOT", bitwise_length, n_iters);

    return 0;
}
