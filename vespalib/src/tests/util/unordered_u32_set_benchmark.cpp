// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/unordered_u32_set.h>

#include <benchmark/benchmark.h>

#include <random>
#include <unordered_set>

struct FewCollisions {
    constexpr static uint32_t apply(uint32_t v) noexcept { return v; }
};

struct ManyCollisions {
    constexpr static uint32_t apply(uint32_t v) noexcept {
        // Multiplying with a power of two will cause massive clustering of
        // keys for AND-based table lookups since their LSBs will be the same.
        return v * 128;
    }
};

// Xorshift linear-feedback shift register (LFSR) PRNG with a period of 2^32-1.
// See https://en.wikipedia.org/wiki/Xorshift.
// Pre -and postcondition: `state` is non-zero (which happens to be exactly what we want).
inline void xorshift32_step(uint32_t& state) noexcept {
    uint32_t x = state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    state = x;
}

// To ensure cosmic fairness and good fortune, this initial PRNG seed value is the
// 32 MSBs of the SHA-256 of the most blessed string "no bli det liv, rai rai!".
constexpr uint32_t initial_seed = 0x876ca8fb;

template <typename SetT, typename KeyTransform> static void BM_insert_unique_from_empty(benchmark::State& state) {
    for (auto _ : state) {
        state.PauseTiming();
        SetT set;
        state.ResumeTiming();
        benchmark::ClobberMemory();
        // We use an LFSR to insert in an "as-if randomized" order that still guarantees
        // that none of the inserted values are duplicated (unlike if we used a generic PRNG).
        uint32_t prng_state = initial_seed;
        for (ssize_t i = 1; i <= state.range(); ++i) { // assume range always < UINT32_MAX
            auto res = set.insert(KeyTransform::apply(prng_state));
            xorshift32_step(prng_state);
            benchmark::DoNotOptimize(res);
        }
        benchmark::DoNotOptimize(set);
        benchmark::ClobberMemory();
    }
}

template <typename SetT, typename KeyTransform> static void BM_insert_unique_from_pre_sized(benchmark::State& state) {
    const uint32_t range_len = static_cast<uint32_t>(state.range());
    for (auto _ : state) {
        state.PauseTiming();
        SetT set(range_len);
        state.ResumeTiming();
        benchmark::ClobberMemory();
        uint32_t prng_state = initial_seed;
        for (uint32_t i = 1; i <= range_len; ++i) { // assume range always < UINT32_MAX
            auto res = set.insert(KeyTransform::apply(prng_state));
            xorshift32_step(prng_state);
            benchmark::DoNotOptimize(res);
        }
        benchmark::DoNotOptimize(set);
        benchmark::ClobberMemory();
    }
}

template <typename SetT, typename KeyTransform> static void BM_lookup_existing(benchmark::State& state) {
    const uint32_t range_len = static_cast<uint32_t>(state.range());
    SetT           set(range_len);
    for (uint32_t i = 1; i <= range_len; ++i) { // assume range always < UINT32_MAX/128
        (void)set.insert(KeyTransform::apply(i));
    }
    assert(set.size() == range_len);
    benchmark::ClobberMemory();
    // For simplicity, we spray&pray with pseudo-random values in the inserted
    // range. The use of uniform_int_distribution adds a bit of constant overhead
    // to each iteration, but it's the same for each set type so it evens out.
    std::minstd_rand              prng;
    std::uniform_int_distribution dist(1u, range_len);
    for (auto _ : state) {
        auto ret = set.contains(KeyTransform::apply(dist(prng)));
        assert(ret);
        benchmark::DoNotOptimize(ret);
    }
}

template <typename SetT, typename KeyTransform> static void BM_lookup_missing(benchmark::State& state) {
    const uint32_t range_len = static_cast<uint32_t>(state.range());
    SetT           set(range_len);
    for (uint32_t i = 1; i <= range_len; ++i) { // assume range always <= INT32_MAX/128
        (void)set.insert(KeyTransform::apply(i));
    }
    assert(set.size() == range_len);
    benchmark::ClobberMemory();
    std::minstd_rand              prng;
    std::uniform_int_distribution dist(1u, UINT32_MAX);
    for (auto _ : state) {
        // Toggle MSB so that no matter what value we end up with, it won't match
        // any of the inserted values.
        const uint32_t k = KeyTransform::apply(dist(prng)) | 0x80000000;
        auto           ret = set.contains(k);
        assert(!ret);
        benchmark::DoNotOptimize(ret);
    }
}

constexpr static uint32_t range_from = 8;
constexpr static uint32_t range_to = 8 << 20;

/// Fill set with N distinct elements from an empty state

BENCHMARK(BM_insert_unique_from_empty<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::UnorderedU32Set, FewCollisions>)->Range(range_from, range_to);

BENCHMARK(BM_insert_unique_from_empty<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_empty<vespalib::UnorderedU32Set, ManyCollisions>)->Range(range_from, range_to);

/// Fill set with N distinct elements when presized to capacity of N

BENCHMARK(BM_insert_unique_from_pre_sized<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::UnorderedU32Set, FewCollisions>)->Range(range_from, range_to);

BENCHMARK(BM_insert_unique_from_pre_sized<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_insert_unique_from_pre_sized<vespalib::UnorderedU32Set, ManyCollisions>)->Range(range_from, range_to);

/// Lookup elements that are known to exist in the set

BENCHMARK(BM_lookup_existing<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to << 2);
BENCHMARK(BM_lookup_existing<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to << 2);
BENCHMARK(BM_lookup_existing<vespalib::UnorderedU32Set, FewCollisions>)->Range(range_from, range_to << 2);

BENCHMARK(BM_lookup_existing<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_existing<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_existing<vespalib::UnorderedU32Set, ManyCollisions>)->Range(range_from, range_to);

/// Lookup elements that are known to _not_ exist in the set

BENCHMARK(BM_lookup_missing<std::unordered_set<uint32_t>, FewCollisions>)->Range(range_from, range_to << 2);
BENCHMARK(BM_lookup_missing<vespalib::hash_set<uint32_t>, FewCollisions>)->Range(range_from, range_to << 2);
BENCHMARK(BM_lookup_missing<vespalib::UnorderedU32Set, FewCollisions>)->Range(range_from, range_to << 2);

BENCHMARK(BM_lookup_missing<std::unordered_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_missing<vespalib::hash_set<uint32_t>, ManyCollisions>)->Range(range_from, range_to);
BENCHMARK(BM_lookup_missing<vespalib::UnorderedU32Set, ManyCollisions>)->Range(range_from, range_to);

BENCHMARK_MAIN();
