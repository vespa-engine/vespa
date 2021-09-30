// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/portal/handle_manager.h>
#include <atomic>

using namespace vespalib;
using namespace vespalib::portal;

TEST_F("require that handles can be created, locked and destroyed", TimeBomb(60)) {
    HandleManager manager;
    uint64_t handle = manager.create();
    EXPECT_TRUE(handle != manager.null_handle());
    {
        HandleGuard guard = manager.lock(handle);
        EXPECT_TRUE(guard.valid());
        EXPECT_EQUAL(guard.handle(), handle);
    }
    EXPECT_TRUE(manager.destroy(handle));
    {
        HandleGuard guard = manager.lock(handle);
        EXPECT_TRUE(!guard.valid());
        EXPECT_EQUAL(guard.handle(), manager.null_handle());
    }
}

TEST_F("require that multiple guards can be taken for the same handle", TimeBomb(60)) {
    HandleManager manager;
    uint64_t handle = manager.create();
    EXPECT_TRUE(handle != manager.null_handle());
    {
        HandleGuard guard1 = manager.lock(handle);
        HandleGuard guard2 = manager.lock(handle); // <- does not block
        EXPECT_TRUE(guard1.valid());
        EXPECT_EQUAL(guard1.handle(), handle);
        EXPECT_TRUE(guard2.valid());
        EXPECT_EQUAL(guard2.handle(), handle);
    }
    EXPECT_TRUE(manager.destroy(handle));    
}

TEST_F("require that handles are independent", TimeBomb(60)) {
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
        EXPECT_EQUAL(guard1.handle(), handle1);
        EXPECT_TRUE(guard2.valid());
        EXPECT_EQUAL(guard2.handle(), handle2);
        EXPECT_TRUE(manager.destroy(handle3)); // <- does not block
        HandleGuard guard3 = manager.lock(handle3);
        EXPECT_TRUE(!guard3.valid());
        EXPECT_EQUAL(guard3.handle(), manager.null_handle());
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

TEST_MT_FF("require that destroy waits for active handle guards", 2, Fixture(), TimeBomb(60)) {
    if (thread_id == 0) {
        {
            auto guard = f1.manager.lock(f1.handle);
            TEST_BARRIER(); // #1
            EXPECT_TRUE(!f1.gate.await(20ms));
        }
        EXPECT_TRUE(f1.gate.await(60s));
    } else {
        TEST_BARRIER(); // #1
        EXPECT_TRUE(f1.manager.destroy(f1.handle));
        f1.gate.countDown();
    }
}

TEST_MT_FF("require that destroy disables ability to lock handles", 3, Fixture(), TimeBomb(60)) {
    if (thread_id == 0) {
        auto guard = f1.manager.lock(f1.handle);
        ASSERT_TRUE(guard.valid());
        TEST_BARRIER(); // #1
        while (f1.cnt1 == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
        EXPECT_EQUAL(f1.cnt2, 0u);
    } else if (thread_id == 1) {
        TEST_BARRIER(); // #1
        EXPECT_TRUE(f1.manager.destroy(f1.handle));
        EXPECT_EQUAL(f1.cnt1, 1u);
        ++f1.cnt2;
    } else {
        TEST_BARRIER(); // #1
        while (f1.cnt1 == 0) {
            auto guard = f1.manager.lock(f1.handle);
            if (guard.valid()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            } else {
                EXPECT_EQUAL(f1.cnt2, 0u);
                ++f1.cnt1;
            }
        }
    }
}

TEST_MT_FF("require that a single destroy call returns true", 10, Fixture(), TimeBomb(60)) {
    if (thread_id == 0) { // 1 thread here
        auto guard = f1.manager.lock(f1.handle);
        ASSERT_TRUE(guard.valid());
        TEST_BARRIER(); // #1
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    } else { // 'many' threads here
        TEST_BARRIER(); // #1
        if (f1.manager.destroy(f1.handle)) {
            ++f1.cnt1;
        } else {
            ++f1.cnt2;
        }
    }
    TEST_BARRIER(); // #2
    EXPECT_EQUAL(f1.cnt1, 1u);
    EXPECT_GREATER(num_threads, 5u);
    EXPECT_EQUAL(f1.cnt2, (num_threads - 2));
}

TEST_MAIN() { TEST_RUN_ALL(); }
