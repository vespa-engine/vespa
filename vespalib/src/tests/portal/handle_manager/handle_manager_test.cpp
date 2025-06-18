// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/test/time_bomb.h>
#include <vespa/vespalib/portal/handle_manager.h>
#include <atomic>

using namespace vespalib;
using namespace vespalib::portal;
using vespalib::test::Nexus;

TEST(HandleManagerTest, require_that_handles_can_be_created_locked_and_destroyed) {
    TimeBomb f1(60);
    HandleManager manager;
    uint64_t handle = manager.create();
    EXPECT_TRUE(handle != manager.null_handle());
    {
        HandleGuard guard = manager.lock(handle);
        EXPECT_TRUE(guard.valid());
        EXPECT_EQ(guard.handle(), handle);
    }
    EXPECT_TRUE(manager.destroy(handle));
    {
        HandleGuard guard = manager.lock(handle);
        EXPECT_TRUE(!guard.valid());
        EXPECT_EQ(guard.handle(), manager.null_handle());
    }
}

TEST(HandleManagerTest, require_that_multiple_guards_can_be_taken_for_the_same_handle) {
    TimeBomb f1(60);
    HandleManager manager;
    uint64_t handle = manager.create();
    EXPECT_TRUE(handle != manager.null_handle());
    {
        HandleGuard guard1 = manager.lock(handle);
        HandleGuard guard2 = manager.lock(handle); // <- does not block
        EXPECT_TRUE(guard1.valid());
        EXPECT_EQ(guard1.handle(), handle);
        EXPECT_TRUE(guard2.valid());
        EXPECT_EQ(guard2.handle(), handle);
    }
    EXPECT_TRUE(manager.destroy(handle));    
}

TEST(HandleManagerTest, require_that_handles_are_independent) {
    TimeBomb f1(60);
    HandleManager manager;
    uint64_t handle1 = manager.create();
    uint64_t handle2 = manager.create();
    uint64_t handle3 = manager.create();
    EXPECT_TRUE(handle1 != manager.null_handle());
    EXPECT_TRUE(handle2 != manager.null_handle());
    EXPECT_TRUE(handle3 != manager.null_handle());
    EXPECT_TRUE(handle1 != handle2);
    EXPECT_TRUE(handle1 != handle3);
    EXPECT_TRUE(handle2 != handle3);
    {
        HandleGuard guard1 = manager.lock(handle1);
        HandleGuard guard2 = manager.lock(handle2);
        EXPECT_TRUE(guard1.valid());
        EXPECT_EQ(guard1.handle(), handle1);
        EXPECT_TRUE(guard2.valid());
        EXPECT_EQ(guard2.handle(), handle2);
        EXPECT_TRUE(manager.destroy(handle3)); // <- does not block
        HandleGuard guard3 = manager.lock(handle3);
        EXPECT_TRUE(!guard3.valid());
        EXPECT_EQ(guard3.handle(), manager.null_handle());
    }
    EXPECT_TRUE(manager.destroy(handle1));
    EXPECT_TRUE(manager.destroy(handle2));
    EXPECT_TRUE(!manager.destroy(handle3));
}

struct Fixture {
    HandleManager manager;
    uint64_t handle;
    Gate gate;
    std::atomic<size_t> cnt1;
    std::atomic<size_t> cnt2;
    Fixture() : manager(), handle(manager.create()), gate(), cnt1(0), cnt2(0) {}
};

TEST(HandleManagerTest, require_that_destroy_waits_for_active_handle_guards) {
    size_t num_threads = 2;
    Fixture f1;
    TimeBomb f2(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        {
                            auto guard = f1.manager.lock(f1.handle);
                            ctx.barrier(); // #1
                            EXPECT_TRUE(!f1.gate.await(20ms));
                        }
                        EXPECT_TRUE(f1.gate.await(60s));
                    } else {
                        ctx.barrier(); // #1
                        EXPECT_TRUE(f1.manager.destroy(f1.handle));
                        f1.gate.countDown();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(HandleManagerTest, require_that_destroy_disables_ability_to_lock_handles) {
    size_t num_threads = 3;
    Fixture f1;
    TimeBomb f2(60);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    if (thread_id == 0) {
                        auto guard = f1.manager.lock(f1.handle);
                        ASSERT_TRUE(guard.valid());
                        ctx.barrier(); // #1
                        while (f1.cnt1 == 0) {
                            std::this_thread::sleep_for(std::chrono::milliseconds(1));
                        }
                        EXPECT_EQ(f1.cnt2, 0u);
                    } else if (thread_id == 1) {
                        ctx.barrier(); // #1
                        EXPECT_TRUE(f1.manager.destroy(f1.handle));
                        EXPECT_EQ(f1.cnt1, 1u);
                        ++f1.cnt2;
                    } else {
                        ctx.barrier(); // #1
                        while (f1.cnt1 == 0) {
                            auto guard = f1.manager.lock(f1.handle);
                            if (guard.valid()) {
                                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                            } else {
                                EXPECT_EQ(f1.cnt2, 0u);
                                ++f1.cnt1;
                            }
                        }
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(HandleManagerTest, require_that_a_single_destroy_call_returns_true) {
    size_t num_threads = 10;
    Fixture f1;
    TimeBomb f2(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) { // 1 thread here
                        auto guard = f1.manager.lock(f1.handle);
                        ASSERT_TRUE(guard.valid());
                        ctx.barrier(); // #1
                        std::this_thread::sleep_for(std::chrono::milliseconds(1));
                    } else { // 'many' threads here
                        ctx.barrier(); // #1
                        if (f1.manager.destroy(f1.handle)) {
                            ++f1.cnt1;
                        } else {
                            ++f1.cnt2;
                        }
                    }
                    ctx.barrier(); // #2
                    EXPECT_EQ(f1.cnt1, 1u);
                    EXPECT_GT(num_threads, 5u);
                    EXPECT_EQ(f1.cnt2, (num_threads - 2));
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
