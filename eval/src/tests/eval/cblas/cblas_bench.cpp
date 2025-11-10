// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <benchmark/benchmark.h>
#include <cblas.h>
#include <vector>
#include <random>
#include <memory>

constexpr size_t MATRIX_SIZE = 512;
constexpr size_t NUM_RHS_MATRICES = 128;
constexpr size_t MATRIX_ELEMENTS = MATRIX_SIZE * MATRIX_SIZE;

// Helper class to manage benchmark data
class SGEMMBenchmarkData {
public:
    std::vector<float> lhs_matrix;
    std::vector<std::vector<float>> rhs_matrices;
    std::vector<float> result_matrix;

    SGEMMBenchmarkData()
        : lhs_matrix(MATRIX_ELEMENTS),
          rhs_matrices(NUM_RHS_MATRICES, std::vector<float>(MATRIX_ELEMENTS)),
          result_matrix(MATRIX_ELEMENTS)
    {
        // Initialize random number generator with normal distribution
        std::random_device rd;
        std::mt19937 gen(rd());
        std::normal_distribution<float> dist(0.0f, 1.0f);

        // Fill LHS matrix with random values
        for (auto& val : lhs_matrix) {
            val = dist(gen);
        }

        // Fill all RHS matrices with random values
        for (auto& rhs : rhs_matrices) {
            for (auto& val : rhs) {
                val = dist(gen);
            }
        }
    }
};

// Global data instance to avoid setup overhead in benchmarks
static std::unique_ptr<SGEMMBenchmarkData> g_data;

static void ensure_data_initialized() {
    if (!g_data) {
        g_data = std::make_unique<SGEMMBenchmarkData>();
    }
}

// Benchmark: NoTrans x NoTrans
static void BM_SGEMM_NoTrans_NoTrans(benchmark::State& state) {
    ensure_data_initialized();

    size_t rhs_idx = 0;
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasNoTrans, CblasNoTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % NUM_RHS_MATRICES;
        benchmark::DoNotOptimize(g_data->result_matrix.data());
        benchmark::ClobberMemory();
    }

    // Report throughput: 2 * N^3 FLOPs per matrix multiply
    state.SetItemsProcessed(state.iterations());
    state.SetBytesProcessed(state.iterations() * sizeof(float) *
                           (2 * MATRIX_ELEMENTS + MATRIX_ELEMENTS));
}

// Benchmark: NoTrans x Trans
static void BM_SGEMM_NoTrans_Trans(benchmark::State& state) {
    ensure_data_initialized();

    size_t rhs_idx = 0;
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasNoTrans, CblasTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % NUM_RHS_MATRICES;
        benchmark::DoNotOptimize(g_data->result_matrix.data());
        benchmark::ClobberMemory();
    }

    state.SetItemsProcessed(state.iterations());
    state.SetBytesProcessed(state.iterations() * sizeof(float) *
                           (2 * MATRIX_ELEMENTS + MATRIX_ELEMENTS));
}

// Benchmark: Trans x NoTrans
static void BM_SGEMM_Trans_NoTrans(benchmark::State& state) {
    ensure_data_initialized();

    size_t rhs_idx = 0;
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasTrans, CblasNoTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % NUM_RHS_MATRICES;
        benchmark::DoNotOptimize(g_data->result_matrix.data());
        benchmark::ClobberMemory();
    }

    state.SetItemsProcessed(state.iterations());
    state.SetBytesProcessed(state.iterations() * sizeof(float) *
                           (2 * MATRIX_ELEMENTS + MATRIX_ELEMENTS));
}

// Benchmark: Trans x Trans
static void BM_SGEMM_Trans_Trans(benchmark::State& state) {
    ensure_data_initialized();

    size_t rhs_idx = 0;
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasTrans, CblasTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % NUM_RHS_MATRICES;
        benchmark::DoNotOptimize(g_data->result_matrix.data());
        benchmark::ClobberMemory();
    }

    state.SetItemsProcessed(state.iterations());
    state.SetBytesProcessed(state.iterations() * sizeof(float) *
                           (2 * MATRIX_ELEMENTS + MATRIX_ELEMENTS));
}

BENCHMARK(BM_SGEMM_NoTrans_NoTrans);
BENCHMARK(BM_SGEMM_NoTrans_Trans);
BENCHMARK(BM_SGEMM_Trans_NoTrans);
BENCHMARK(BM_SGEMM_Trans_Trans);

int main(int argc, char *argv[]) {
    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
