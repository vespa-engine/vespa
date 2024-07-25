// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/generic.h>
#include <vespa/vespalib/hwaccelerated/hwy_impl.h>
#include <vespa/vespalib/util/time.h>
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
    printf("sum=%f of N=%zu and vector length=%zu took %" PRId64 "\n", sumOfSums, count, sz, count_ms(elapsed));
}

void
benchMarkEuclideanDistance(const hwaccelerated::IAccelerated & accelerator, size_t sz, size_t count) {
    auto euclidean_dist_fn = [&accelerator](const auto* lhs, const auto* rhs, size_t my_sz) {
        return accelerator.squaredEuclideanDistance(lhs, rhs, my_sz);
    };
    printf("double : ");
    benchmark_fn<double>(euclidean_dist_fn, sz, count);
    printf("float  : ");
    benchmark_fn<float>(euclidean_dist_fn, sz, count);
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

int main(int argc, char *argv[]) {
    int length = 1000;
    int count = 1000000;
    if (argc > 1) {
        length = atol(argv[1]);
    }
    if (argc > 2) {
        count = atol(argv[2]);
    }
    printf("%s %d %d\n", argv[0], length, count);
    printf("Squared Euclidean Distance - Generic\n");
    benchMarkEuclideanDistance(hwaccelerated::GenericAccelerator(), length, count);
    printf("Squared Euclidean Distance - Optimized for this cpu\n");
    benchMarkEuclideanDistance(hwaccelerated::IAccelerated::getAccelerator(), length, count);
    printf("Squared Euclidean Distance - Highway\n");
    benchMarkEuclideanDistance(hwaccelerated::HwyAccelerator(), length, count);

    printf("\n");
    printf("Dot Product - Generic\n");
    benchmark_dot_product(hwaccelerated::GenericAccelerator(), length, count);
    printf("Dot Product - Optimized for this cpu\n");
    benchmark_dot_product(hwaccelerated::IAccelerated::getAccelerator(), length, count);
    printf("Dot Product - Highway\n");
    benchmark_dot_product(hwaccelerated::HwyAccelerator(), length, count);

    printf("\n");
    printf("Popcount - Generic\n");
    benchmark_popcount(hwaccelerated::GenericAccelerator(), length, count);
    printf("Popcount - Optimized for this cpu\n");
    benchmark_popcount(hwaccelerated::IAccelerated::getAccelerator(), length, count);
    printf("Popcount - Highway\n");
    benchmark_popcount(hwaccelerated::HwyAccelerator(), length, count);

    return 0;
}
