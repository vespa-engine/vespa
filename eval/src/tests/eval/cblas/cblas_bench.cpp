// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <benchmark/benchmark.h>
#include <cblas.h>
#include <vector>
#include <random>
#include <memory>
#include <iostream>
#include <cstring>

constexpr size_t MATRIX_SIZE = 512;
constexpr size_t MATRIX_ELEMENTS = MATRIX_SIZE * MATRIX_SIZE;

// Global configuration - tunable via command-line
size_t g_num_rhs_matrices = 16;

// Helper class to manage benchmark data
class SGEMMBenchmarkData {
public:
    std::vector<float> lhs_matrix;
    std::vector<std::vector<float>> rhs_matrices;
    std::vector<float> result_matrix;

    explicit SGEMMBenchmarkData(size_t num_rhs)
        : lhs_matrix(MATRIX_ELEMENTS),
          rhs_matrices(num_rhs, std::vector<float>(MATRIX_ELEMENTS)),
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

    size_t num_rhs_matrices() const { return rhs_matrices.size(); }
};

// Global data instance to avoid setup overhead in benchmarks
static std::unique_ptr<SGEMMBenchmarkData> g_data;

static void ensure_data_initialized() {
    if (!g_data) {
        g_data = std::make_unique<SGEMMBenchmarkData>(g_num_rhs_matrices);
        std::cout << "Initialized with " << g_data->num_rhs_matrices() << " RHS matrices\n";
    }
}

// Benchmark: NoTrans x NoTrans
static void BM_SGEMM_NoTrans_NoTrans(benchmark::State& state) {
    ensure_data_initialized();

    size_t rhs_idx = 0;
    const size_t num_matrices = g_data->num_rhs_matrices();
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasNoTrans, CblasNoTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % num_matrices;
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
    const size_t num_matrices = g_data->num_rhs_matrices();
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasNoTrans, CblasTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % num_matrices;
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
    const size_t num_matrices = g_data->num_rhs_matrices();
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasTrans, CblasNoTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % num_matrices;
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
    const size_t num_matrices = g_data->num_rhs_matrices();
    for (auto _ : state) {
        cblas_sgemm(CblasRowMajor, CblasTrans, CblasTrans,
                    MATRIX_SIZE, MATRIX_SIZE, MATRIX_SIZE,
                    1.0f,  // alpha
                    g_data->lhs_matrix.data(), MATRIX_SIZE,
                    g_data->rhs_matrices[rhs_idx].data(), MATRIX_SIZE,
                    0.0f,  // beta
                    g_data->result_matrix.data(), MATRIX_SIZE);

        rhs_idx = (rhs_idx + 1) % num_matrices;
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
    // Parse custom arguments before benchmark::Initialize
    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "--num_rhs_matrices") == 0 && i + 1 < argc) {
            g_num_rhs_matrices = std::atoi(argv[i + 1]);
            if (g_num_rhs_matrices == 0) {
                std::cerr << "Error: --num_rhs_matrices must be a positive integer\n";
                return 1;
            }
            // Remove these arguments so benchmark::Initialize doesn't see them
            for (int j = i; j < argc - 2; ++j) {
                argv[j] = argv[j + 2];
            }
            argc -= 2;
            --i;
        } else if (std::strcmp(argv[i], "--help") == 0 || std::strcmp(argv[i], "-h") == 0) {
            std::cout << "Custom options:\n";
            std::cout << "  --num_rhs_matrices <N>  Number of RHS matrices to use (default: 16)\n";
            std::cout << "\n";
        }
    }

    benchmark::Initialize(&argc, argv);
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();

    return 0;
}
