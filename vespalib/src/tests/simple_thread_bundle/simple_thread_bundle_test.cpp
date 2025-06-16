// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/box.h>
#include <vespa/vespalib/util/small_vector.h>
#include <vespa/vespalib/util/gate.h>
#include <thread>
#include <forward_list>
#include <cassert>

using namespace vespalib;
using namespace vespalib::fixed_thread_bundle;
using vespalib::test::Nexus;

struct Cnt : Runnable {
    size_t x;
    Cnt() noexcept : x(0) {}
    void run() override { ++x; }
};

struct State {
    std::vector<Cnt> cnts;
    State(size_t n) : cnts(n) {}
    std::vector<Runnable*> getTargets(size_t n) {
        assert(n <= cnts.size());
        std::vector<Runnable*> targets;
        for (size_t i = 0; i < n; ++i) {
            targets.push_back(&cnts[i]);
        }
        return targets;
    }
    void check(const std::vector<size_t> &expect) {
        ASSERT_LE(expect.size(), cnts.size());
        for (size_t i = 0; i < expect.size(); ++i) {
            ASSERT_EQ(expect[i], cnts[i].x);
        }
    }
};

struct Blocker : Runnable {
    Gate start;
    ~Blocker() override;
    void run() override {
        start.await();
    }
    Gate done; // set externally
};

Blocker::~Blocker() = default;

TEST(SimpleThreadBundleTest, require_that_signals_can_be_counted_and_cancelled) {
    size_t num_threads = 2;
    Signal f1;
    size_t f2 = 16000;
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        for (size_t i = 0; i < f2; ++i) {
                            f1.send();
                            if (i % 128 == 0) { std::this_thread::sleep_for(1ms); }
                        }
                        ctx.barrier();
                        f1.cancel();
                    } else {
                        size_t localGen = 0;
                        size_t diffSum = 0;
                        while (localGen < f2) {
                            size_t diff = f1.wait(localGen);
                            EXPECT_GT(diff, 0u);
                            diffSum += diff;
                        }
                        EXPECT_EQ(f2, localGen);
                        EXPECT_EQ(f2, diffSum);
                        ctx.barrier();
                        EXPECT_EQ(0u, f1.wait(localGen));
                        EXPECT_EQ(f2 + 1, localGen);
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(SimpleThreadBundleTest, require_that_bundles_of_size_0_cannot_be_created) {
    VESPA_EXPECT_EXCEPTION(SimpleThreadBundle(0), IllegalArgumentException, "");
}

TEST(SimpleThreadBundleTest, require_that_bundles_with_no_internal_threads_work) {
    SimpleThreadBundle f1(1);
    State f2(1);
    f1.run(f2.getTargets(1));
    f2.check(Box<size_t>().add(1));
}

TEST(SimpleThreadBundleTest, require_that_bundles_can_be_run_without_targets) {
    SimpleThreadBundle f1(1);
    State f2(1);
    f1.run(f2.getTargets(0));
    f2.check(Box<size_t>().add(0));
}

TEST(SimpleThreadBundleTest, require_that_having_too_many_targets_fails) {
    SimpleThreadBundle f1(1);
    State f2(2);
    VESPA_EXPECT_EXCEPTION(f1.run(f2.getTargets(2)), IllegalArgumentException, "");
    f2.check(Box<size_t>().add(0).add(0));
}

TEST(SimpleThreadBundleTest, require_that_ThreadBundle__trivial_works_the_same_as_SimpleThreadBundle_1) {
    State f(2);
    ThreadBundle &bundle = ThreadBundle::trivial();
    EXPECT_EQ(bundle.size(), 1u);
    bundle.run(f.getTargets(0));
    f.check({0,0});
    bundle.run(f.getTargets(1));
    f.check({1,0});
    VESPA_EXPECT_EXCEPTION(bundle.run(f.getTargets(2)), IllegalArgumentException, "");
    f.check({1,0});
}

TEST(SimpleThreadBundleTest, require_that_bundles_with_multiple_internal_threads_work) {
    SimpleThreadBundle f1(3);
    State f2(3);
    f1.run(f2.getTargets(3));
    f2.check(Box<size_t>().add(1).add(1).add(1));
}

TEST(SimpleThreadBundleTest, require_that_bundles_can_be_used_multiple_times) {
    SimpleThreadBundle f1(3);
    State f2(3);
    f1.run(f2.getTargets(3));
    f1.run(f2.getTargets(3));
    f1.run(f2.getTargets(3));
    f2.check(Box<size_t>().add(3).add(3).add(3));
}

TEST(SimpleThreadBundleTest, require_that_bundles_can_be_used_with_fewer_than_maximum_threads) {
    SimpleThreadBundle f1(3);
    State f2(3);
    f1.run(f2.getTargets(3));
    f1.run(f2.getTargets(2));
    f1.run(f2.getTargets(1));
    f2.check(Box<size_t>().add(3).add(2).add(1));
}

TEST(SimpleThreadBundleTest, require_that_bundle_run_waits_for_all_targets) {
    size_t num_threads = 2;
    SimpleThreadBundle f1(4);
    State f2(3);
    Blocker f3;
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        std::vector<Runnable*> targets = f2.getTargets(3);
                        targets.push_back(&f3);
                        f1.run(targets);
                        f2.check(Box<size_t>().add(1).add(1).add(1));
                        f3.done.countDown();
                    } else {
                        EXPECT_FALSE(f3.done.await(20ms));
                        f3.start.countDown();
                        EXPECT_TRUE(f3.done.await(10s));
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(SimpleThreadBundleTest, require_that_all_strategies_work_with_variable_number_of_threads_and_targets) {
    std::vector<SimpleThreadBundle::Strategy> strategies
        = make_box(SimpleThreadBundle::USE_SIGNAL_LIST,
                   SimpleThreadBundle::USE_SIGNAL_TREE,
                   SimpleThreadBundle::USE_BROADCAST);
    for (size_t s = 0; s < strategies.size(); ++s) {
        for (size_t t = 1; t <= 16; ++t) {
            State state(t);
            SimpleThreadBundle threadBundle(t, strategies[s]);
            for (size_t r = 0; r <= t; ++r) {
                threadBundle.run(state.getTargets(r));
            }
            std::vector<size_t> expect;
            for (size_t e = 0; e < t; ++e) {
                expect.push_back(t - e);
            }
            ASSERT_NO_FATAL_FAILURE(state.check(expect)) << "s: " << s << ", t: " << t;
        }
    }
}

TEST(SimpleThreadBundleTest, require_that_bundle_pool_gives_out_bundles) {
    SimpleThreadBundle::Pool f1(5);
    auto b1 = f1.getBundle();
    auto b2 = f1.getBundle();
    EXPECT_EQ(5u, b1.bundle().size());
    EXPECT_EQ(5u, b2.bundle().size());
    EXPECT_FALSE(&b1.bundle() == &b2.bundle());
}

TEST(SimpleThreadBundleTest, require_that_bundles_do_not_need_to_be_put_back_on_the_pool) {
    SimpleThreadBundle::Pool f1(5);
    SimpleThreadBundle::UP b1 = f1.obtain();
    ASSERT_TRUE(b1.get() != nullptr);
    EXPECT_EQ(5u, b1->size());
}

TEST(SimpleThreadBundleTest, require_that_bundle_pool_reuses_bundles) {
    SimpleThreadBundle::Pool f1(5);
    SimpleThreadBundle *ptr;
    {
        ptr = &f1.getBundle().bundle();
    }
    auto bundle = f1.getBundle();
    EXPECT_EQ(ptr, &bundle.bundle());
}

TEST(SimpleThreadBundleTest, require_that_bundle_pool_works_with_multiple_threads) {
    size_t num_threads = 32;
    SimpleThreadBundle::Pool f1(3);
    std::vector<SimpleThreadBundle*> f2(num_threads, nullptr);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    SimpleThreadBundle::Pool::Guard bundle = f1.getBundle();
                    EXPECT_EQ(3u, bundle.bundle().size());
                    f2[thread_id] = &bundle.bundle();
                    ctx.barrier();
                    if (thread_id == 0) {
                        for (size_t i = 0; i < num_threads; ++i) {
                            for (size_t j = 0; j < num_threads; ++j) {
                                EXPECT_EQ((f2[i] == f2[j]), (i == j));
                            }
                        }
                    }
                    ctx.barrier();
                };
    Nexus::run(num_threads, task);
}

struct Filler {
    int stuff;
    Filler() : stuff(0) {}
    virtual ~Filler() = default;
};

struct Proxy : Filler, Runnable {
    Runnable &target;
    Proxy(Runnable &target_in) : target(target_in) {}
    void run() override { target.run(); }
};

struct AlmostRunnable : Runnable {};

TEST(SimpleThreadBundleTest, require_that_Proxy_needs_fixup_to_become_Runnable) {
    Cnt cnt;
    Proxy proxy(cnt);
    Runnable &runnable = proxy;
    void *proxy_ptr = &proxy;
    void *runnable_ptr = &runnable;
    EXPECT_TRUE(proxy_ptr != runnable_ptr);
}

TEST(SimpleThreadBundleTest, require_that_various_versions_of_run_can_be_used_to_invoke_targets) {
    SimpleThreadBundle f1(5);
    State f2(5);
    EXPECT_TRUE(thread_bundle::direct_dispatch_array<std::vector<Runnable*>>);
    EXPECT_TRUE(thread_bundle::direct_dispatch_array<SmallVector<Runnable*>>);
    EXPECT_TRUE(thread_bundle::direct_dispatch_array<std::initializer_list<Runnable*>>);
    EXPECT_TRUE(thread_bundle::direct_dispatch_array<std::vector<Runnable::UP>>);
    EXPECT_TRUE(thread_bundle::direct_dispatch_array<SmallVector<Runnable::UP>>);
    EXPECT_TRUE(thread_bundle::direct_dispatch_array<std::initializer_list<Runnable::UP>>);
    EXPECT_FALSE(thread_bundle::direct_dispatch_array<std::forward_list<Runnable*>>);
    EXPECT_FALSE(thread_bundle::direct_dispatch_array<std::vector<std::unique_ptr<Proxy>>>);
    EXPECT_FALSE(thread_bundle::direct_dispatch_array<std::vector<std::unique_ptr<AlmostRunnable>>>);
    std::vector<Runnable::UP> direct;
    std::vector<std::unique_ptr<Proxy>> custom;
    for (Runnable &target: f2.cnts) {
        direct.push_back(std::make_unique<Proxy>(target));
        custom.push_back(std::make_unique<Proxy>(target));
    }
    std::vector<Runnable*> refs = f2.getTargets(5);
    f2.check({0,0,0,0,0});
    f1.run(refs.data(), 3);   // baseline
    f2.check({1,1,1,0,0});
    f1.run(&refs[3], 2);      // baseline
    f2.check({1,1,1,1,1});
    f1.run(f2.getTargets(5)); // const fast dispatch
    f2.check({2,2,2,2,2});
    f1.run(refs);             // non-const fast dispatch
    f2.check({3,3,3,3,3});
    f1.run(direct);           // fast dispatch with transparent UP
    f2.check({4,4,4,4,4});
    f1.run(custom);           // fall-back with runnable subclass UP
    f2.check({5,5,5,5,5});
    f1.run(f2.cnts);          // fall-back with resolved reference (actual objects)
    f2.check({6,6,6,6,6});
    std::initializer_list<std::reference_wrapper<Cnt>> list = {f2.cnts[0], f2.cnts[1], f2.cnts[2], f2.cnts[3], f2.cnts[4]};
    f1.run(list);             // fall-back with resolved reference (reference wrapper)
    f2.check({7,7,7,7,7});
    std::initializer_list<Runnable*> list2 = {&f2.cnts[0], &f2.cnts[1], &f2.cnts[2], &f2.cnts[3], &f2.cnts[4]};
    f1.run(list2);            // fast dispatch with non-vector range
    f2.check({8,8,8,8,8});
    std::forward_list<Runnable*> run_list(list2);
    f1.run(run_list);         // fall-back with non-sized range
    f2.check({9,9,9,9,9});
    vespalib::SmallVector<Runnable*> my_vec(list2);
    f1.run(my_vec);           // fast dispatch with custom container
    f2.check({10,10,10,10,10});
}

GTEST_MAIN_RUN_ALL_TESTS()
