// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <barrier>
#include <concepts>

using namespace vespalib;
using vespalib::test::Nexus;
using vespalib::test::ThreadMeets;

struct BarrierBench : ::testing::TestWithParam<size_t> {};

double measure_ms(auto &use_barrier, size_t n) {
    auto t1 = steady_clock::now();
    for (size_t i = 0; i < n; ++i) {
        use_barrier();
    }
    auto t2 = steady_clock::now();
    return to_s(t2 - t1) * 1000.0;
}

struct use_std_barrier {
    std::barrier<> barrier;
    use_std_barrier(size_t num_threads) : barrier(num_threads) {}
    void operator()() { barrier.arrive_and_wait(); }
};

struct use_vespalib_barrier {
    Barrier barrier;
    use_vespalib_barrier(size_t num_threads) : barrier(num_threads) {}
    void operator()() { barrier.await(); }
};

struct use_rendezvous {
    ThreadMeets::Nop barrier;
    use_rendezvous(size_t num_threads) : barrier(num_threads) {}
    void operator()() { barrier(); }
};

size_t loop_cnt = 10000;
std::vector<size_t> num_threads_list{1, 2, 3, 4, 6, 8, 16, 32, 64};

template <typename T>
class TypedBarrierBench : public ::testing::Test {};

TYPED_TEST_SUITE_P(TypedBarrierBench);

TYPED_TEST_P(TypedBarrierBench, barrier_speed) {
    for (size_t num_threads: num_threads_list) {
        TypeParam use_barrier(num_threads);
        auto task = [&](Nexus &ctx){
                        measure_ms(use_barrier, 100); // warm_up
                        double res = measure_ms(use_barrier, loop_cnt);
                        if (ctx.thread_id() == 0) {
                            fprintf(stderr, "%2zu threads: %6zu iterations: %9.3f ms\n", num_threads, loop_cnt, res);
                        }
                    };
        Nexus::run(num_threads, task);
    }
}

REGISTER_TYPED_TEST_SUITE_P(TypedBarrierBench, barrier_speed);

using BarrierList = ::testing::Types<use_std_barrier, use_vespalib_barrier, use_rendezvous>;
INSTANTIATE_TYPED_TEST_SUITE_P(BarrierBench, TypedBarrierBench, BarrierList);

GTEST_MAIN_RUN_ALL_TESTS()
