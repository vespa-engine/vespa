// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/nice.h>
#include <vespa/vespalib/test/thread_meets.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <unistd.h>
#include <functional>
#include <thread>

using vespalib::Runnable;
using vespalib::be_nice;
using vespalib::test::ThreadMeets;

double how_nice(int now, int target) {
    int max = 19;
    int wanted_param = (target - now);
    int num_zones = ((max + 1) - now);
    // make sure we are in the middle of the wanted nice zone
    double result = (0.5 + wanted_param) / num_zones;
    fprintf(stderr, " ... using how_nice=%g to get from %d to %d in nice value\n", result, now, target);
    return result;
}

struct RunFun : Runnable {
    std::function<void()> my_fun;
    RunFun(std::function<void()> fun_in) : my_fun(fun_in) {}
    void run() override { my_fun(); }
};

int my_init_fun(Runnable &target) {
    target.run();
    return 1;
}

std::thread run_with_init(std::function<void()> my_fun, Runnable::init_fun_t init_fun = my_init_fun) {
    return std::thread([init_fun, my_fun]
                       {
                           RunFun run_fun(my_fun);
                           init_fun(run_fun);
                       });
}

TEST(NiceTest, require_that_initial_nice_value_is_0) {
    EXPECT_EQ(nice(0), 0);
}

TEST(NiceTest, require_that_nice_value_is_tracked_per_thread) {
    ThreadMeets::Nop barrier(5);
    std::vector<std::thread> threads;
    for (int i = 0; i < 5; ++i) {
        threads.push_back(run_with_init([my_barrier = &barrier, i]
                                        {
                                            [[maybe_unused]] auto nice_result = nice(i);
                                            (*my_barrier)();
                                            EXPECT_EQ(nice(0), i);
                                        }));
    }
    for (auto &thread: threads) {
        thread.join();
    }
}

void verify_max_nice_value() {
    int now = nice(0);
    now = nice(19 - now);
    EXPECT_EQ(now, 19);
    now = nice(1);
    EXPECT_EQ(now, 19);
}

TEST(NiceTest, require_that_max_nice_value_is_19) {
    auto thread = run_with_init([]{ verify_max_nice_value(); });
    thread.join();
}

TEST(NiceTest, require_that_nice_value_can_be_set_with_init_function) {
    for (int i = 0; i <= 19; ++i) {
        auto thread = run_with_init([i]()
                                    {
                                        EXPECT_EQ(nice(0), i);
                                    }, be_nice(my_init_fun, how_nice(0, i)));
        thread.join();
    }
}

TEST(NiceTest, require_that_niceness_can_be_nested_and_will_act_on_a_limited_nice_value_range) {
    auto thread1 = run_with_init([]{ EXPECT_EQ(nice(0), 7); },
                                 be_nice(be_nice(my_init_fun, how_nice(3, 7)), how_nice(0, 3)));
    auto thread2 = run_with_init([]{ EXPECT_EQ(nice(0), 15); },
                                 be_nice(be_nice(my_init_fun, how_nice(10, 15)), how_nice(0, 10)));
    auto thread3 = run_with_init([]{ EXPECT_EQ(nice(0), 19); },
                                 be_nice(be_nice(my_init_fun, how_nice(10, 19)), how_nice(0, 10)));
    thread1.join();
    thread2.join();
    thread3.join();
}

GTEST_MAIN_RUN_ALL_TESTS()
