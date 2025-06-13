// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/util/count_down_latch.h>

using namespace vespalib;
using vespalib::test::Nexus;

struct Fixture {
    Barrier barrier;
    CountDownLatch latch;
    Fixture(size_t n) : barrier(n), latch(n) {}
};

TEST(BarrierTest, require_that_barriers_are_satisfied_by_the_appropriate_number_of_threads) {
    size_t num_threads = 3;
    Fixture f1(num_threads);    
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        f1.latch.countDown();
                        EXPECT_FALSE(f1.latch.await(250ms));
                        EXPECT_TRUE(f1.barrier.await());
                        EXPECT_TRUE(f1.latch.await(25s));
                    } else {
                        EXPECT_TRUE(f1.barrier.await());
                        f1.latch.countDown();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(BarrierTest, require_that_barriers_can_be_used_multiple_times) {
    size_t num_threads = 3;
    Fixture f1(num_threads);
    auto task = [&](Nexus &ctx){
                    EXPECT_TRUE(f1.barrier.await());
                    EXPECT_TRUE(f1.barrier.await());
                    if (ctx.thread_id() == 0) {
                        f1.latch.countDown();
                        EXPECT_FALSE(f1.latch.await(250ms));
                        EXPECT_TRUE(f1.barrier.await());
                        EXPECT_TRUE(f1.latch.await(25s));
                    } else {
                        EXPECT_TRUE(f1.barrier.await());
                        f1.latch.countDown();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(BarrierTest, require_that_barriers_can_be_broken) {
    size_t num_threads = 3;
    Fixture f1(num_threads);
    auto task = [&](Nexus &ctx){
                    EXPECT_TRUE(f1.barrier.await());
                    if (ctx.thread_id() == 0) {
                        f1.latch.countDown();
                        EXPECT_FALSE(f1.latch.await(250ms));
                        f1.barrier.destroy();
                        EXPECT_TRUE(f1.latch.await(25s));
                    } else {
                        EXPECT_FALSE(f1.barrier.await());
                        f1.latch.countDown();
                    }
                    EXPECT_FALSE(f1.barrier.await());
                };
    Nexus::run(num_threads, task);
}

TEST(BarrierTest, require_that_barriers_cannot_be_retroactively_broken) {
    size_t num_threads = 100;
    Barrier f1(num_threads);
    auto task = [&](Nexus &){
                    EXPECT_TRUE(f1.await());
                    f1.destroy();
                    EXPECT_FALSE(f1.await());
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
