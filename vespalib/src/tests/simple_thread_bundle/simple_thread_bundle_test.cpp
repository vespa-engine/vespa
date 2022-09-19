// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/box.h>
#include <vespa/vespalib/util/small_vector.h>
#include <thread>
#include <forward_list>

using namespace vespalib;
using namespace vespalib::fixed_thread_bundle;

struct Cnt : Runnable {
    size_t x;
    Cnt() noexcept : x(0) {}
    void run() override { ++x; }
};

struct State {
    std::vector<Cnt> cnts;
    State(size_t n) : cnts(n) {}
    std::vector<Runnable*> getTargets(size_t n) {
        ASSERT_LESS_EQUAL(n, cnts.size());
        std::vector<Runnable*> targets;
        for (size_t i = 0; i < n; ++i) {
            targets.push_back(&cnts[i]);
        }
        return targets;
    }
    bool check(const std::vector<size_t> &expect) {
        bool status = true;
        ASSERT_LESS_EQUAL(expect.size(), cnts.size());
        for (size_t i = 0; i < expect.size(); ++i) {
            status &= EXPECT_EQUAL(expect[i], cnts[i].x);
        }
        return status;
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

TEST_MT_FF("require that signals can be counted and cancelled", 2, Signal, size_t(16000)) {
    if (thread_id == 0) {
        for (size_t i = 0; i < f2; ++i) {
            f1.send();
            if (i % 128 == 0) { std::this_thread::sleep_for(1ms); }
        }
        TEST_BARRIER();
        f1.cancel();
    } else {
        size_t localGen = 0;
        size_t diffSum = 0;
        while (localGen < f2) {
            size_t diff = f1.wait(localGen);
            EXPECT_GREATER(diff, 0u);
            diffSum += diff;
        }
        EXPECT_EQUAL(f2, localGen);
        EXPECT_EQUAL(f2, diffSum);
        TEST_BARRIER();
        EXPECT_EQUAL(0u, f1.wait(localGen));
        EXPECT_EQUAL(f2 + 1, localGen);
    }
}

TEST("require that bundles of size 0 cannot be created") {
    EXPECT_EXCEPTION(SimpleThreadBundle(0), IllegalArgumentException, "");
}

TEST_FF("require that bundles with no internal threads work", SimpleThreadBundle(1), State(1)) {
    f1.run(f2.getTargets(1));
    f2.check(Box<size_t>().add(1));
}

TEST_FF("require that bundles can be run without targets", SimpleThreadBundle(1), State(1)) {
    f1.run(f2.getTargets(0));
    f2.check(Box<size_t>().add(0));
}

TEST_FF("require that having too many targets fails", SimpleThreadBundle(1), State(2)) {
    EXPECT_EXCEPTION(f1.run(f2.getTargets(2)), IllegalArgumentException, "");
    f2.check(Box<size_t>().add(0).add(0));
}

TEST_F("require that ThreadBundle::trivial works the same as SimpleThreadBundle(1)", State(2)) {
    ThreadBundle &bundle = ThreadBundle::trivial();
    EXPECT_EQUAL(bundle.size(), 1u);
    bundle.run(f.getTargets(0));
    f.check({0,0});
    bundle.run(f.getTargets(1));
    f.check({1,0});
    EXPECT_EXCEPTION(bundle.run(f.getTargets(2)), IllegalArgumentException, "");
    f.check({1,0});
}

TEST_FF("require that bundles with multiple internal threads work", SimpleThreadBundle(3), State(3)) {
    f1.run(f2.getTargets(3));
    f2.check(Box<size_t>().add(1).add(1).add(1));
}

TEST_FF("require that bundles can be used multiple times", SimpleThreadBundle(3), State(3)) {
    f1.run(f2.getTargets(3));
    f1.run(f2.getTargets(3));
    f1.run(f2.getTargets(3));
    f2.check(Box<size_t>().add(3).add(3).add(3));
}

TEST_FF("require that bundles can be used with fewer than maximum threads", SimpleThreadBundle(3), State(3)) {
    f1.run(f2.getTargets(3));
    f1.run(f2.getTargets(2));
    f1.run(f2.getTargets(1));
    f2.check(Box<size_t>().add(3).add(2).add(1));
}

TEST_MT_FFF("require that bundle run waits for all targets", 2, SimpleThreadBundle(4), State(3), Blocker) {
    if (thread_id == 0) {
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
}

TEST("require that all strategies work with variable number of threads and targets") {
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
            if (!state.check(expect)) {
                fprintf(stderr, "s:%zu, t:%zu\n", s, t);
            }
        }
    }
}

TEST_F("require that bundle pool gives out bundles", SimpleThreadBundle::Pool(5)) {
    SimpleThreadBundle::UP b1 = f1.obtain();
    SimpleThreadBundle::UP b2 = f1.obtain();
    ASSERT_TRUE(b1.get() != 0);
    ASSERT_TRUE(b2.get() != 0);
    EXPECT_EQUAL(5u, b1->size());
    EXPECT_EQUAL(5u, b2->size());
    EXPECT_FALSE(b1.get() == b2.get());
    f1.release(std::move(b1));
    f1.release(std::move(b2));
}

TEST_F("require that bundles do not need to be put back on the pool", SimpleThreadBundle::Pool(5)) {
    SimpleThreadBundle::UP b1 = f1.obtain();
    ASSERT_TRUE(b1.get() != 0);
    EXPECT_EQUAL(5u, b1->size());
}

TEST_F("require that bundle pool reuses bundles", SimpleThreadBundle::Pool(5)) {
    SimpleThreadBundle::UP bundle = f1.obtain();
    SimpleThreadBundle *ptr = bundle.get();
    f1.release(std::move(bundle));
    bundle = f1.obtain();
    EXPECT_EQUAL(ptr, bundle.get());
}

TEST_MT_FF("require that bundle pool works with multiple threads", 32, SimpleThreadBundle::Pool(3),
           std::vector<SimpleThreadBundle*>(num_threads, 0))
{
    SimpleThreadBundle::UP bundle = f1.obtain();
    ASSERT_TRUE(bundle.get() != 0);
    EXPECT_EQUAL(3u, bundle->size());
    f2[thread_id] = bundle.get();
    TEST_BARRIER();
    if (thread_id == 0) {
        for (size_t i = 0; i < num_threads; ++i) {
            for (size_t j = 0; j < num_threads; ++j) {
                EXPECT_EQUAL((f2[i] == f2[j]), (i == j));
            }
        }
    }
    TEST_BARRIER();
    f1.release(std::move(bundle));
}

struct Filler {
    int stuff;
    Filler() : stuff(0) {}
    virtual ~Filler() {}
};

struct Proxy : Filler, Runnable {
    Runnable &target;
    Proxy(Runnable &target_in) : target(target_in) {}
    void run() override { target.run(); }
};

struct AlmostRunnable : Runnable {};

TEST("require that Proxy needs fixup to become Runnable") {
    Cnt cnt;
    Proxy proxy(cnt);
    Runnable &runnable = proxy;
    void *proxy_ptr = &proxy;
    void *runnable_ptr = &runnable;
    EXPECT_TRUE(proxy_ptr != runnable_ptr);
}

TEST_FF("require that various versions of run can be used to invoke targets", SimpleThreadBundle(5), State(5)) {
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

TEST_MAIN() { TEST_RUN_ALL(); }
