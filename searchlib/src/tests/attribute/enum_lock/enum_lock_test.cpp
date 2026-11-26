// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/enum_lock.h>
#include <vespa/searchlib/attribute/enummodifier.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <chrono>
#include <future>
#include <mutex>
#include <shared_mutex>
#include <thread>

using namespace std::chrono_literals;
using search::AttributeFactory;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::EnumLock;

namespace {

// Long enough to never time out on a loaded test machine when nothing is wrong.
constexpr auto hang_timeout = 60s;

template <typename Pred> bool wait_until(Pred pred) {
    auto deadline = std::chrono::steady_clock::now() + hang_timeout;
    while (!pred()) {
        if (std::chrono::steady_clock::now() >= deadline) {
            return false;
        }
        std::this_thread::sleep_for(1ms);
    }
    return true;
}

template <typename T> bool is_ready(std::future<T>& future, std::chrono::milliseconds timeout) {
    return future.wait_for(timeout) == std::future_status::ready;
}

} // namespace

TEST(EnumLockTest, shared_lock_can_be_released_by_another_thread) {
    EnumLock lock;
    lock.lock_shared();
    EXPECT_EQ(1u, lock.readers());
    std::thread([&lock] { lock.unlock_shared(); }).join();
    EXPECT_EQ(0u, lock.readers());
    EXPECT_TRUE(lock.try_lock());
    lock.unlock();
}

TEST(EnumLockTest, exclusive_lock_can_be_released_by_another_thread) {
    EnumLock lock;
    lock.lock();
    std::thread([&lock] { lock.unlock(); }).join();
    EXPECT_TRUE(lock.try_lock_shared());
    lock.unlock_shared();
}

TEST(EnumLockTest, try_lock_fails_while_read_held_and_succeeds_when_free) {
    EnumLock lock;
    {
        std::shared_lock<EnumLock> reader(lock);
        EXPECT_FALSE(lock.try_lock());
        EXPECT_TRUE(lock.try_lock_shared());
        lock.unlock_shared();
    }
    EXPECT_TRUE(lock.try_lock());
    EXPECT_FALSE(lock.try_lock());
    EXPECT_FALSE(lock.try_lock_shared());
    lock.unlock();
}

/*
 * In the tests below, futures of threads that wait for a lock are declared before the guards
 * they wait for. If an ASSERT returns early, the guards are then released before the future
 * destructors wait for those threads, and the test fails instead of hanging.
 */

TEST(EnumLockTest, waiting_writer_does_not_block_a_reader_that_already_holds_the_lock) {
    EnumLock                   lock;
    std::future<void>          writer;
    std::future<void>          other;
    std::shared_lock<EnumLock> first(lock);
    writer = std::async(std::launch::async, [&lock] { std::unique_lock<EnumLock> w(lock); });
    ASSERT_TRUE(wait_until([&lock] { return lock.waiting_writers() == 1; }));

    // Same thread, second shared lock, with a writer waiting: must not block.
    std::shared_lock<EnumLock> second(lock);
    EXPECT_EQ(2u, lock.readers());
    // Another thread taking a shared lock is not blocked by the waiting writer either.
    other = std::async(std::launch::async, [&lock] { std::shared_lock<EnumLock> r(lock); });
    EXPECT_TRUE(is_ready(other, hang_timeout));

    EXPECT_FALSE(is_ready(writer, 0ms));
    second.unlock();
    EXPECT_FALSE(is_ready(writer, 10ms)); // still one reader
    first.unlock();
    EXPECT_TRUE(is_ready(writer, hang_timeout));
    EXPECT_EQ(0u, lock.waiting_writers());
}

TEST(EnumLockTest, writer_waits_for_reader_released_by_another_thread) {
    EnumLock                   lock;
    std::future<void>          writer;
    std::shared_lock<EnumLock> reader(lock);
    writer = std::async(std::launch::async, [&lock] { std::unique_lock<EnumLock> w(lock); });
    ASSERT_TRUE(wait_until([&lock] { return lock.waiting_writers() == 1; }));
    EXPECT_FALSE(is_ready(writer, 10ms));
    std::thread([reader = std::move(reader)]() mutable noexcept { reader.unlock(); }).join();
    EXPECT_TRUE(is_ready(writer, hang_timeout));
}

TEST(EnumLockTest, reader_waits_for_active_writer) {
    EnumLock                   lock;
    std::future<void>          reader;
    std::unique_lock<EnumLock> writer(lock);
    reader = std::async(std::launch::async, [&lock] { std::shared_lock<EnumLock> r(lock); });
    EXPECT_FALSE(is_ready(reader, 10ms));
    writer.unlock();
    EXPECT_TRUE(is_ready(reader, hang_timeout));
}

/*
 * The scenario that deadlocks with a writer-preferring std::shared_mutex: a thread holding a
 * stable enum guard takes another on the same attribute while enum store compaction waits for
 * the enum lock, and the guards are released by other threads than the ones that took them.
 */
TEST(EnumLockTest, stable_enum_guards_on_an_attribute_with_compaction_pending) {
    using GuardUP = std::unique_ptr<search::attribute::AttributeReadGuard>;
    auto attr = AttributeFactory::createAttribute("a", Config(BasicType::STRING, CollectionType::ARRAY));
    ASSERT_TRUE(attr->hasEnum());
    const auto&          lock = attr->enum_lock();
    std::future<void>    compaction;
    std::future<GuardUP> second;
    GuardUP              first = attr->makeReadGuard(true);
    EXPECT_TRUE(attr->hasActiveEnumGuards());

    compaction =
        std::async(std::launch::async, [&attr] { search::attribute::EnumModifier m(attr->getEnumModifier()); });
    ASSERT_TRUE(wait_until([&lock] { return lock.waiting_writers() == 1; }));

    second = std::async(std::launch::async, [&attr] { return attr->makeReadGuard(true); });
    ASSERT_TRUE(is_ready(second, hang_timeout));
    GuardUP third = attr->makeReadGuard(true);
    EXPECT_EQ(3u, lock.readers());
    EXPECT_EQ(1u, lock.waiting_writers());

    // Release on other threads than the ones that took them.
    std::thread([g = std::move(first)]() mutable noexcept { g.reset(); }).join();
    std::thread([g = second.get()]() mutable noexcept { g.reset(); }).join();
    EXPECT_FALSE(is_ready(compaction, 10ms)); // third is still held
    std::thread([g = std::move(third)]() mutable noexcept { g.reset(); }).join();
    EXPECT_TRUE(is_ready(compaction, hang_timeout));
    EXPECT_FALSE(attr->hasActiveEnumGuards());
}

GTEST_MAIN_RUN_ALL_TESTS()
