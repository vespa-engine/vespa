// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <vespa/vespalib/util/time.h>
#include <cinttypes>

using namespace vespalib;
using namespace vespalib::hwaccelerated;

template <typename T, typename Fn>
void benchmark_fn(Fn f, size_t sz, size_t count) {
    auto [a, b] = create_and_fill_lhs_rhs<T>(count);
    steady_time start = steady_clock::now();
    double sumOfSums(0);
    for (size_t j(0); j < count; j++) {
        double sum = f(a.data(), b.data(), sz);
        sumOfSums += sum;
    }
    duration elapsed = steady_clock::now() - start;
    printf("sum=%f of N=%zu and vector length=%zu took %.2f ms\n", sumOfSums, count, sz,
           std::chrono::duration<double, std::milli>(elapsed).count());
}

void benchmark_squared_euclidean_distance(const IAccelerated& accelerator, size_t sz, size_t count) {
    auto euclidean_dist_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.squaredEuclideanDistance(lhs, rhs, my_sz);
    };
    printf("double : ");
    benchmark_fn<double>(euclidean_dist_fn, sz, count);
    printf("float  : ");
    benchmark_fn<float>(euclidean_dist_fn, sz, count);
    printf("BF16   : ");
    benchmark_fn<BFloat16>(euclidean_dist_fn, sz, count);
    printf("int8_t : ");
    benchmark_fn<int8_t>(euclidean_dist_fn, sz, count);
}

void benchmark_dot_product(const IAccelerated& accelerator, size_t sz, size_t count) {
    auto dot_product_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.dotProduct(lhs, rhs, my_sz);
    };
    printf("double : ");
    benchmark_fn<double>(dot_product_fn, sz, count);
    printf("float  : ");
    benchmark_fn<float>(dot_product_fn, sz, count);
    printf("BF16   : ");
    benchmark_fn<BFloat16>(dot_product_fn, sz, count);
    printf("int8_t : ");
    benchmark_fn<int8_t>(dot_product_fn, sz, count);
}

void benchmark_popcount(const IAccelerated& accelerator, size_t sz, size_t count) {
    auto popcount_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky
        return accelerator.populationCount(lhs, my_sz);
    };
    printf("uint64_t : ");
    benchmark_fn<uint64_t>(popcount_fn, sz, count);
}

void for_each_hwy_target(auto&& fn) {
    const auto hwy_targets = Highway::create_supported_targets();
    for (const auto& t : hwy_targets) {
        fn(*t);
    }
}

int main(int argc, char *argv[]) {
    int length = 1000;
    int count = 1000000;
    if (argc > 1) {
        length = atol(argv[1]);
    }
    if (argc > 2) {
        count = atol(argv[2]);
    }
    auto baseline_accel      = IAccelerated::create_platform_baseline_accelerator();
    const auto& native_accel = IAccelerated::getAccelerator();

    printf("%s %d %d\n", argv[0], length, count);
    // Run a single warmup run to crank up the CPU power budget enough that any downclocking
    // should be immediately visible. Use the widest ("most optimal") available vectors (e.g.
    // AVX-512 on x64) for this, since it's the most susceptible to throttling.
    printf("Squared Euclidean Distance - Warmup round (%s)\n", native_accel.target_name());
    benchmark_squared_euclidean_distance(native_accel, length, count);
    printf("\n");

    for_each_hwy_target([&](const IAccelerated& hwy_accel) {
        printf("Squared Euclidean Distance - Highway (%s)\n", hwy_accel.target_name());
        benchmark_squared_euclidean_distance(hwy_accel, length, count);
    });
    printf("Squared Euclidean Distance - Legacy baseline (%s)\n", baseline_accel->target_name());
    benchmark_squared_euclidean_distance(*baseline_accel, length, count);
    printf("Squared Euclidean Distance - Legacy optimized for this CPU (%s)\n", native_accel.target_name());
    benchmark_squared_euclidean_distance(native_accel, length, count);

    printf("\n");
    for_each_hwy_target([&](const IAccelerated& hwy_accel) {
        printf("Dot Product - Highway (%s)\n", hwy_accel.target_name());
        benchmark_dot_product(hwy_accel, length, count);
    });
    printf("Dot Product - Legacy baseline (%s)\n", baseline_accel->target_name());
    benchmark_dot_product(*baseline_accel, length, count);
    printf("Dot Product - Legacy optimized for this CPU (%s)\n", native_accel.target_name());
    benchmark_dot_product(native_accel, length, count);

    printf("\n");
    for_each_hwy_target([&](const IAccelerated& hwy_accel) {
        printf("Popcount - Highway (%s)\n", hwy_accel.target_name());
        benchmark_popcount(hwy_accel, length, count);
    });
    printf("Popcount - Legacy baseline (%s)\n", baseline_accel->target_name());
    benchmark_popcount(*baseline_accel, length, count);
    printf("Popcount - Legacy optimized for this CPU (%s)\n", native_accel.target_name());
    benchmark_popcount(native_accel, length, count);

    return 0;
}
