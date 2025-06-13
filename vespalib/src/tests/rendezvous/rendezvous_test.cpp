// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <utility>
#include <thread>

using namespace vespalib;
using vespalib::test::Nexus;

struct Value {
    size_t value;
    Value() : value(42) {}
};

template <typename T, bool ext_id>
struct Empty : Rendezvous<int, T, ext_id> {
    Empty(size_t n) : Rendezvous<int, T, ext_id>(n) {}
    ~Empty() override;
    void mingle() override {}
    T meet(size_t thread_id) {
        if constexpr (ext_id) {
            return this->rendezvous(0, thread_id);
        } else {
            (void) thread_id;
            return this->rendezvous(0);
        }
    }
};

template <typename T, bool ext_id>
Empty<T, ext_id>::~Empty() = default;

template <bool ext_id>
struct Add : Rendezvous<size_t, std::pair<size_t, size_t>, ext_id> {
    using Super = Rendezvous<size_t, std::pair<size_t, size_t>, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    Add(size_t n) : Super(n) {}
    ~Add() override;
    void mingle() override {
        size_t sum = 0;
        for (size_t i = 0; i < size(); ++i) {
            sum += in(i);
        }
        for (size_t i = 0; i < this->size(); ++i) {
            out(i) = std::make_pair(sum, in(0));
        }
    }
};

template <bool ext_id>
Add<ext_id>::~Add() = default;

template <bool ext_id>
struct Modify : Rendezvous<size_t, size_t, ext_id> {
    using Super = Rendezvous<size_t, size_t, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    Modify(size_t n) : Super(n) {}
    ~Modify() override;
    void mingle() override {
        for (size_t i = 0; i < size(); ++i) {
            in(i) += 1;
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = in(i);
        }
    }
};

template <bool ext_id>
Modify<ext_id>::~Modify() = default;

template <typename T, bool ext_id>
struct Swap : Rendezvous<T, T, ext_id> {
    using Super = Rendezvous<T, T, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    Swap() : Super(2) {}
    ~Swap() override;
    void mingle() override {
        out(0) = std::move(in(1));
        out(1) = std::move(in(0));
    }
};

template <typename T, bool ext_id>
Swap<T, ext_id>::~Swap() = default;

template <bool ext_id>
struct DetectId : Rendezvous<int, size_t, ext_id> {
    using Super = Rendezvous<int, size_t, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    DetectId(size_t n) : Super(n) {}
    ~DetectId() override;
    void mingle() override {
        for (size_t i = 0; i < size(); ++i) {
            out(i) = i;
        }
    }
    size_t meet(size_t thread_id) {
        if constexpr (ext_id) {
            return this->rendezvous(0, thread_id);
        } else {
            (void) thread_id;
            return this->rendezvous(0);
        }
    }
};

template <bool ext_id>
DetectId<ext_id>::~DetectId() = default;

struct Any : Rendezvous<bool, bool> {
    Any(size_t n) : Rendezvous<bool, bool>(n) {}
    ~Any() override;
    void mingle() override {
        bool result = false;
        for (size_t i = 0; i < size(); ++i) {
            result |= in(i);
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = result;
        }
    }
    bool check(bool flag) { return this->rendezvous(flag); }
};

Any::~Any() = default;

TEST(RendezvousTest, require_that_creating_an_empty_rendezvous_will_fail) {
    VESPA_EXPECT_EXCEPTION(Add<false>(0), IllegalArgumentException, "");
    VESPA_EXPECT_EXCEPTION(Add<true>(0), IllegalArgumentException, "");
}

TEST(RendezvousTest, require_that_a_single_thread_can_mingle_with_itself_within_a_rendezvous) {
    Add<false> f1(1);
    Add<true> f2(1);
    size_t thread_id = 0;
    EXPECT_EQ(10u, f1.rendezvous(10).first);
    EXPECT_EQ(20u, f1.rendezvous(20).first);
    EXPECT_EQ(30u, f1.rendezvous(30).first);
    EXPECT_EQ(10u, f2.rendezvous(10, thread_id).first);
    EXPECT_EQ(20u, f2.rendezvous(20, thread_id).first);
    EXPECT_EQ(30u, f2.rendezvous(30, thread_id).first);
}

TEST(RendezvousTest, require_that_rendezvous_can_mingle_multiple_threads) {
    size_t num_threads = 10;
    Add<false> f1(num_threads);
    Add<true> f2(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    EXPECT_EQ(45u, f1.rendezvous(thread_id).first);
                    EXPECT_EQ(45u, f2.rendezvous(thread_id, thread_id).first);
                };
    Nexus::run(num_threads, task);
}

template <bool ext_id> using Empty1 = Empty<Value, ext_id>;
template <bool ext_id> using Empty2 = Empty<size_t, ext_id>;

TEST(RendezvousTest, require_that_unset_rendezvous_outputs_are_default_constructed) {
    size_t num_threads = 10;
    Empty1<false> f1(num_threads);
    Empty2<false> f2(num_threads);
    Empty1<true> f3(num_threads);
    Empty2<true> f4(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    EXPECT_EQ(42u, f1.meet(thread_id).value);
                    EXPECT_EQ(0u, f2.meet(thread_id));
                    EXPECT_EQ(42u, f3.meet(thread_id).value);
                    EXPECT_EQ(0u, f4.meet(thread_id));
                };
    Nexus::run(num_threads, task);
}

TEST(RendezvousTest, require_that_mingle_is_not_called_until_all_threads_are_present) {
    size_t num_threads = 3;
    Add<false> f1(num_threads);
    CountDownLatch f2(num_threads - 1);
    Add<true> f3(num_threads);
    CountDownLatch f4(num_threads - 1);
    auto task = [&](Nexus &ctx) {
                    auto thread_id = ctx.thread_id();
                    for (bool ext_id: {false, true}) {
                        CountDownLatch &latch = ext_id ? f4 : f2;
                        if (thread_id == 0) {
                            EXPECT_FALSE(latch.await(20ms));
                            if (ext_id) {
                                EXPECT_EQ(3u, f3.rendezvous(thread_id, thread_id).first);
                            } else {
                                EXPECT_EQ(3u, f1.rendezvous(thread_id).first);
                            }
                            EXPECT_TRUE(latch.await(25s));
                        } else {
                            if (ext_id) {
                                EXPECT_EQ(3u, f3.rendezvous(thread_id, thread_id).first);
                            } else {
                                EXPECT_EQ(3u, f1.rendezvous(thread_id).first);
                            }
                            latch.countDown();
                        }
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(RendezvousTest, require_that_rendezvous_can_be_used_multiple_times) {
    size_t num_threads = 10;
    Add<false> f1(num_threads);
    Add<true> f2(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    EXPECT_EQ(45u, f1.rendezvous(thread_id).first);
                    EXPECT_EQ(45u, f2.rendezvous(thread_id, thread_id).first);
                    EXPECT_EQ(45u, f1.rendezvous(thread_id).first);
                    EXPECT_EQ(45u, f2.rendezvous(thread_id, thread_id).first);
                    EXPECT_EQ(45u, f1.rendezvous(thread_id).first);
                    EXPECT_EQ(45u, f2.rendezvous(thread_id, thread_id).first);
                };
    Nexus::run(num_threads, task);
}

TEST(RendezvousTest, require_that_rendezvous_can_be_run_with_additional_threads) {
    size_t num_threads = 100;
    Add<false> f1(10);
    CountDownLatch f2(10);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    auto res = f1.rendezvous(thread_id);
                    ctx.barrier();
                    if (res.second == thread_id) {
                        EXPECT_EQ(4950u, f1.rendezvous(res.first).first);
                        f2.countDown();
                    }
                    EXPECT_TRUE(f2.await(25s));
                };
    Nexus::run(num_threads, task);
}

TEST(RendezvousTest, require_that_mingle_can_modify_its_own_copy_of_input_values) {
    size_t num_threads = 10;
    Modify<false> f1(num_threads);
    Modify<true> f2(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    size_t my_input = thread_id;
                    size_t my_output1 = f1.rendezvous(my_input);
                    size_t my_output2 = f2.rendezvous(my_input, thread_id);
                    EXPECT_EQ(my_input, thread_id);
                    EXPECT_EQ(my_output1, thread_id + 1);
                    EXPECT_EQ(my_output2, thread_id + 1);
                };
    Nexus::run(num_threads, task);
}

using Swap_false = Swap<std::unique_ptr<size_t>,false>;
using Swap_true = Swap<std::unique_ptr<size_t>,true>;

TEST(RendezvousTest, require_that_threads_can_exchange_non_copyable_state) {
    size_t num_threads = 2;
    Swap_false f1;
    Swap_true f2;
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    auto other1 = f1.rendezvous(std::make_unique<size_t>(thread_id));
                    EXPECT_EQ(*other1, 1 - thread_id);
                    auto other2 = f2.rendezvous(std::make_unique<size_t>(thread_id), thread_id);
                    EXPECT_EQ(*other2, 1 - thread_id);
                };
    Nexus::run(num_threads, task);
}

TEST(RendezvousTest, require_that_participation_id_can_be_explicitly_defined) {
    size_t num_threads = 10;
    DetectId<true> f1(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    for (size_t i = 0; i < 128; ++i) {
                        size_t my_id = f1.meet(thread_id);
                        EXPECT_EQ(my_id, thread_id);
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(RendezvousTest, require_that_participation_id_is_unstable_when_not_explicitly_defined) {
    size_t num_threads = 10;
    DetectId<false> f1(num_threads);
    Any f2(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    bool id_mismatch = false;
                    size_t old_id = f1.meet(thread_id);
                    for (size_t i = 0; !id_mismatch; ++i) {
                        if ((i % num_threads) == thread_id) {
                            std::this_thread::sleep_for(std::chrono::milliseconds(i));
                        }
                        size_t new_id = f1.meet(thread_id);
                        if (new_id != old_id) {
                            id_mismatch = true;
                        }
                        id_mismatch = f2.check(id_mismatch);
                    }
                    EXPECT_TRUE(id_mismatch);
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
