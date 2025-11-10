// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <benchmark/benchmark.h>

// TODO: Add actual CBLAS benchmark functions here
// Examples:
// - SGEMM (single-precision general matrix multiply)
// - DGEMM (double-precision general matrix multiply)
// - SGEMV (single-precision general matrix-vector multiply)
// - DDOT (double-precision dot product)

static void BM_DummyCBLAS(benchmark::State& state) {
    // Dummy benchmark - replace with actual CBLAS operations
    for (auto _ : state) {
        volatile int x = 0;
        benchmark::DoNotOptimize(x);
    }
}

BENCHMARK(BM_DummyCBLAS);

int main(int argc, char *argv[]) {
    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
