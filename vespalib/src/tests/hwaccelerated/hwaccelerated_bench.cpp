// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/hwy_impl.h>
#include <vespa/vespalib/util/time.h>
#include <hwy/targets.h> // TODO temp
#include <cinttypes>

using namespace vespalib;

template<typename T>
std::vector<T> createAndFill(size_t sz) {
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = rand()%128;
    }
    return v;
}

template <typename T, typename Fn>
void
benchmark_fn(Fn f, size_t sz, size_t count) {
    srand(1);
    std::vector<T> a = createAndFill<T>(sz);
    std::vector<T> b = createAndFill<T>(sz);
    steady_time start = steady_clock::now();
    double sumOfSums(0);
    for (size_t j(0); j < count; j++) {
        double sum = f(&a[0], &b[0], sz);
        sumOfSums += sum;
    }
    duration elapsed = steady_clock::now() - start;
    printf("sum=%f of N=%zu and vector length=%zu took %.2f ms\n", sumOfSums, count, sz,
           std::chrono::duration<double, std::milli>(elapsed).count());
}

void
benchmark_squared_euclidean_distance(const hwaccelerated::IAccelerated& accelerator, size_t sz, size_t count) {
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

void
benchmark_dot_product(const hwaccelerated::IAccelerated& accelerator, size_t sz, size_t count) {
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

void
benchmark_popcount(const hwaccelerated::IAccelerated& accelerator, size_t sz, size_t count) {
    auto popcount_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        (void)rhs; // ... a little bit sneaky
        return accelerator.populationCount(lhs, my_sz);
    };
    printf("uint64_t : ");
    benchmark_fn<uint64_t>(popcount_fn, sz, count);
}

void for_each_hwy_target(auto&& fn) {
    for (auto target : hwy::SupportedAndGeneratedTargets()) {
        hwy::SetSupportedTargetsForTest(target);
        fn();
    }
    hwy::SetSupportedTargetsForTest(0);
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
    auto baseline_accel      = hwaccelerated::IAccelerated::create_platform_baseline_accelerator();
    const auto& native_accel = hwaccelerated::IAccelerated::getAccelerator();
    // On x64 we require AVX2 as a baseline, so don't bother wasting time testing SSSE3/SSE4.
    // Ideally we would not even build these targets. No effect on Aarch64.
    hwy::DisableTargets(HWY_SSSE3 | HWY_SSE4);

    printf("%s %d %d\n", argv[0], length, count);
    printf("Squared Euclidean Distance - Baseline (%s)\n", baseline_accel->target_name());
    benchmark_squared_euclidean_distance(*baseline_accel, length, count);
    printf("Squared Euclidean Distance - Optimized for this CPU (%s)\n", native_accel.target_name());
    benchmark_squared_euclidean_distance(native_accel, length, count);
    for_each_hwy_target([&] {
        printf("Squared Euclidean Distance - Highway (%s)\n", hwaccelerated::HwyAccelerator().target_name());
        benchmark_squared_euclidean_distance(hwaccelerated::HwyAccelerator(), length, count);
    });

    printf("\n");
    printf("Dot Product - Baseline (%s)\n", baseline_accel->target_name());
    benchmark_dot_product(*baseline_accel, length, count);
    printf("Dot Product - Optimized for this CPU (%s)\n", native_accel.target_name());
    benchmark_dot_product(native_accel, length, count);
    for_each_hwy_target([&] {
        printf("Dot Product - Highway (%s)\n", hwaccelerated::HwyAccelerator().target_name());
        benchmark_dot_product(hwaccelerated::HwyAccelerator(), length, count);
    });

    printf("\n");
    printf("Popcount - Baseline (%s)\n", baseline_accel->target_name());
    benchmark_popcount(*baseline_accel, length, count);
    printf("Popcount - Optimized for this CPU (%s)\n", native_accel.target_name());
    benchmark_popcount(native_accel, length, count);
    for_each_hwy_target([&] {
        printf("Popcount - Highway (%s)\n", hwaccelerated::HwyAccelerator().target_name());
        benchmark_popcount(hwaccelerated::HwyAccelerator(), length, count);
    });

    return 0;
}
