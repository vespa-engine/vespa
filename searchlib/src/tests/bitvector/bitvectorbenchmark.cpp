// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <benchmark/benchmark.h>
#include <cassert>
#include <memory>
#include <random>
#include <string>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("bitvector_benchmark");

namespace search {

struct BitVectorBenchmark : benchmark::Fixture {
    std::vector<std::unique_ptr<BitVector>> _bv;
    std::vector<unsigned int> _bvc;

    BitVectorBenchmark();
    ~BitVectorBenchmark() override;

    void SetUp(const benchmark::State& state) override {
        const size_t n = state.range();
        auto a = BitVector::create(n);
        auto b = BitVector::create(n);
        std::minstd_rand prng;
        prng.seed(1);
        std::uniform_int_distribution<size_t> dist10(0, 9); // closed interval
        for (size_t i = 0; i < n; i += dist10(prng)) {
            a->flipBit(i);
        }
        for (size_t i = 0; i < n; i += dist10(prng)) {
            b->flipBit(i);
        }
        a->invalidateCachedCount();
        b->invalidateCachedCount();
        _bvc.push_back(a->countTrueBits());
        _bv.emplace_back(std::move(a));
        _bvc.push_back(b->countTrueBits());
        _bv.emplace_back(std::move(b));
    }

    void TearDown(const benchmark::State&) override {
        _bv.clear();
        _bvc.clear();
    }
};

BitVectorBenchmark::BitVectorBenchmark() = default;
BitVectorBenchmark::~BitVectorBenchmark() = default;

BENCHMARK_DEFINE_F(BitVectorBenchmark, or_speed)(benchmark::State& state) {
    for (auto _ : state) {
        _bv[0]->orWith(*_bv[1]);
    }
    state.SetItemsProcessed(state.range() * state.iterations());
}

BENCHMARK_DEFINE_F(BitVectorBenchmark, count_speed)(benchmark::State& state) {
    for (auto _ : state) {
        _bv[0]->invalidateCachedCount();
        unsigned int cnt = _bv[0]->countTrueBits();
        assert(cnt == _bvc[0]);
        benchmark::DoNotOptimize(cnt);
    }
    state.SetItemsProcessed(state.range() * state.iterations());
}

template <typename BitFn>
void do_benchmark_fn_in_state_bit_range(benchmark::State& state, BitFn bit_fn) {
    std::minstd_rand prng;
    prng.seed(1);
    assert(state.range() > 0);
    std::uniform_int_distribution<size_t> dist(0, state.range() - 1); // closed interval
    for (auto _ : state) {
        auto result = bit_fn(dist(prng));
        benchmark::DoNotOptimize(result);
    }
    state.SetItemsProcessed(state.iterations());
}

BENCHMARK_DEFINE_F(BitVectorBenchmark, get_next_true_bit_with_random_bits_set)(benchmark::State& state) {
    do_benchmark_fn_in_state_bit_range(state, [&](size_t bit_idx) {
        return _bv[0]->getNextTrueBit(bit_idx);
    });
}

BENCHMARK_DEFINE_F(BitVectorBenchmark, get_next_false_bit_with_random_bits_set)(benchmark::State& state) {
    do_benchmark_fn_in_state_bit_range(state, [&](size_t bit_idx) {
        return _bv[0]->getNextFalseBit(bit_idx);
    });
}

namespace {

size_t scan(BitVector& bv) __attribute__((noinline));

size_t scan(BitVector& bv) {
    size_t count(0);
    for (BitVector::Index i(bv.getFirstTrueBit()), m(bv.size()); i < m; i = bv.getNextTrueBit(i+1)) {
        count++;
    }
    return count;
}

}

BENCHMARK_DEFINE_F(BitVectorBenchmark, get_next_true_bit_scan_with_random_bits_set)(benchmark::State& state) {
    for (auto _ : state) {
        auto result = scan(*_bv[0]);
        benchmark::DoNotOptimize(result);
    }
    state.SetItemsProcessed(state.range() * state.iterations());
}

BENCHMARK_DEFINE_F(BitVectorBenchmark, get_next_true_bit_scan_with_all_bits_set)(benchmark::State& state) {
    auto bv = BitVector::create(state.range());
    bv->setInterval(0, bv->size());
    for (auto _ : state) {
        auto result = scan(*bv);
        benchmark::DoNotOptimize(result);
    }
    state.SetItemsProcessed(state.range() * state.iterations());
}


BENCHMARK_REGISTER_F(BitVectorBenchmark, or_speed)->RangeMultiplier(4)->Range(1024, 8<<22);
BENCHMARK_REGISTER_F(BitVectorBenchmark, count_speed)->RangeMultiplier(4)->Range(1024, 8<<22);
// Test with large bit vectors to determine effect of last level cache misses
BENCHMARK_REGISTER_F(BitVectorBenchmark, get_next_true_bit_with_random_bits_set)->RangeMultiplier(8)->Range(1024, 8<<25);
BENCHMARK_REGISTER_F(BitVectorBenchmark, get_next_false_bit_with_random_bits_set)->RangeMultiplier(8)->Range(1024, 8<<25);

BENCHMARK_REGISTER_F(BitVectorBenchmark, get_next_true_bit_scan_with_random_bits_set)->RangeMultiplier(8)->Range(1024, 8<<22);
BENCHMARK_REGISTER_F(BitVectorBenchmark, get_next_true_bit_scan_with_all_bits_set)->RangeMultiplier(8)->Range(1024, 8<<22);

} // search

BENCHMARK_MAIN();
