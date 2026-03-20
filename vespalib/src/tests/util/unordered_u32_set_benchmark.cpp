// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/unordered_u32_set.h>
#include <benchmark/benchmark.h>
#include <unordered_set>

struct FewCollisions {
    constexpr static uint32_t apply(uint32_t v) noexcept {
        return v;
    }
};

struct ManyCollisions {
    constexpr static uint32_t apply(uint32_t v) noexcept {
        // Multiplying with a power of two will cause massive clustering of
        // keys for AND-based table lookups since their LSBs will be the same.
        return v * 128;
    }
};

template <typename SetT, typename KeyTransform>
static void BM_insert_unique_from_empty(benchmark::State& state) {
    for (auto _ : state) {
        state.PauseTiming();
        SetT set{};
        state.ResumeTiming();
        benchmark::ClobberMemory();
        for (ssize_t i = 1; i <= state.range(); ++i) { // assume range always < UINT32_MAX
            auto res = set.insert(KeyTransform::apply(i));
            benchmark::DoNotOptimize(res);
        }
        benchmark::DoNotOptimize(set);
        benchmark::ClobberMemory();
    }
}

template <typename SetT, typename KeyTransform>
static void BM_insert_unique_from_pre_sized(benchmark::State& state) {
    for (auto _ : state) {
        state.PauseTiming();
        SetT set{static_cast<uint32_t>(state.range())};
        state.ResumeTiming();
        benchmark::ClobberMemory();
        for (ssize_t i = 1; i <= state.range(); ++i) { // assume range always < UINT32_MAX
            auto res = set.insert(KeyTransform::apply(i));
            benchmark::DoNotOptimize(res);
        }
        benchmark::DoNotOptimize(set);
        benchmark::ClobberMemory();
    }
}

template <typename SetT, typename KeyTransform>
static void BM_lookup_existing(benchmark::State& state) {
    SetT set{};
    for (ssize_t i = 1; i <= state.range(); ++i) { // assume range always < UINT32_MAX
        (void)set.insert(KeyTransform::apply(i));
    }
    benchmark::ClobberMemory();
    uint32_t next_lookup = 1;
    for (auto _ : state) {
        auto ret = set.contains(KeyTransform::apply(next_lookup));
        next_lookup = (next_lookup <= state.range()) ? next_lookup + 1 : 1; // Avoid modulo
        benchmark::DoNotOptimize(ret);
    }
}

template <typename SetT, typename KeyTransform>
static void BM_lookup_missing(benchmark::State& state) {
    SetT set{};
    for (ssize_t i = 1; i <= state.range(); ++i) { // assume range always < UINT32_MAX
        (void)set.insert(KeyTransform::apply(i));
    }
    benchmark::ClobberMemory();
    uint32_t next_lookup = state.range() + 1;
    for (auto _ : state) {
        auto ret = set.contains(KeyTransform::apply(next_lookup));
        next_lookup = (next_lookup < UINT32_MAX) ? next_lookup + 1 : state.range() + 1; // Avoid modulo
        benchmark::DoNotOptimize(ret);
    }
}

constexpr static uint32_t range_from = 8;
constexpr static uint32_t range_to   = 8 << 20;

/// Fill set with N distinct elements from an empty state

BENCHMARK(BM_insert_unique_from_empty<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::UnorderedU32Set,    FewCollisions>)->Range(range_from, range_to);

BENCHMARK(BM_insert_unique_from_empty<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::UnorderedU32Set,    ManyCollisions>)->Range(range_from, range_to);

/// Fill set with N distinct elements when presized to capacity of N

BENCHMARK(BM_insert_unique_from_pre_sized<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::UnorderedU32Set,    FewCollisions>)->Range(range_from, range_to);

BENCHMARK(BM_insert_unique_from_pre_sized<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::UnorderedU32Set,    ManyCollisions>)->Range(range_from, range_to);

/// Lookup elements that are known to exist in the set

BENCHMARK(BM_lookup_existing<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_existing<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_existing<vespalib::UnorderedU32Set,    FewCollisions>)->Range(range_from, range_to);

BENCHMARK(BM_lookup_existing<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_existing<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_existing<vespalib::UnorderedU32Set,    ManyCollisions>)->Range(range_from, range_to);

/// Lookup elements that are known to _not_ exist in the set

BENCHMARK(BM_lookup_missing<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_missing<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_missing<vespalib::UnorderedU32Set,    FewCollisions>)->Range(range_from, range_to);

BENCHMARK(BM_lookup_missing<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_missing<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_missing<vespalib::UnorderedU32Set,    ManyCollisions>)->Range(range_from, range_to);

BENCHMARK_MAIN();
