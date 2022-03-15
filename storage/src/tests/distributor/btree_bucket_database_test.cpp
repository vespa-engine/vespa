// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdatabasetest.h"
#include <vespa/storage/bucketdb/btree_bucket_database.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/time.h>
#include <gtest/gtest.h>
#include <atomic>
#include <thread>

using namespace ::testing;

namespace storage::distributor {

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(BTreeDatabase, BucketDatabaseTest,
                                     ::testing::Values(std::make_shared<BTreeBucketDatabase>()));

using document::BucketId;

namespace {

BucketCopy BC(uint32_t node_idx, uint32_t state) {
    api::BucketInfo info(0x123, state, state);
    return BucketCopy(0, node_idx, info);
}


BucketInfo BI(uint32_t node_idx, uint32_t state) {
    BucketInfo bi;
    bi.addNode(BC(node_idx, state), toVector<uint16_t>(0));
    return bi;
}

}

struct BTreeReadGuardTest : Test {
    BTreeBucketDatabase _db;
};

TEST_F(BTreeReadGuardTest, guard_does_not_observe_new_entries) {
    auto guard = _db.acquire_read_guard();
    _db.update(BucketDatabase::Entry(BucketId(16, 16), BI(1, 1234)));

    auto entries = guard->find_parents_and_self(BucketId(16, 16));
    EXPECT_EQ(entries.size(), 0U);
    entries = guard->find_parents_self_and_children(BucketId(16, 16));
    EXPECT_EQ(entries.size(), 0U);
}

TEST_F(BTreeReadGuardTest, guard_observes_entries_alive_at_acquire_time) {
    BucketId bucket(16, 16);
    _db.update(BucketDatabase::Entry(bucket, BI(1, 1234)));
    auto guard = _db.acquire_read_guard();
    _db.remove(bucket);

    auto entries = guard->find_parents_and_self(bucket);
    ASSERT_EQ(entries.size(), 1U);
    EXPECT_EQ(entries[0].getBucketInfo(), BI(1, 1234));

    entries = guard->find_parents_self_and_children(bucket);
    ASSERT_EQ(entries.size(), 1U);
    EXPECT_EQ(entries[0].getBucketInfo(), BI(1, 1234));
}

namespace {

BucketCopy make_bucket_copy(uint16_t node_idx, uint32_t dummy_info) {
    return {0, node_idx, api::BucketInfo(dummy_info, dummy_info, dummy_info)};
}

BucketInfo make_bucket_info(uint32_t dummy_info) {
    BucketInfo bi;
    bi.addNode(make_bucket_copy(0, dummy_info), {0, 1, 2});
    bi.addNode(make_bucket_copy(1, dummy_info), {0, 1, 2});
    bi.addNode(make_bucket_copy(2, dummy_info), {0, 1, 2});
    bi.setLastGarbageCollectionTime(dummy_info);
    return bi;
}

}

// Simple pseudo-stress test with a single writer and a single reader thread.
// The writer thread continuously updates a set of buckets with an array of bucket
// info instances and last GC timestamp that all have the same value, but the value
// itself is incremented for each write. This allows the reader to validate that it
// is observing a stable snapshot across all read values for a given bucket key.
TEST_F(BTreeReadGuardTest, multithreaded_read_guards_observe_stable_snapshots) {
    constexpr uint32_t bucket_bits = 20;
    constexpr uint32_t n_buckets = 1u << 10u; // Must be less than 2**bucket_bits
    constexpr auto duration = 500ms;
    vespalib::CountDownLatch latch(2);
    std::atomic<bool> run_reader(true);

    std::thread reader_thread([&]{
        latch.countDown();
        uint32_t read_counter = 0;
        while (run_reader.load(std::memory_order_relaxed)) {
            auto guard = _db.acquire_read_guard();
            const uint32_t superbucket = (read_counter % n_buckets);
            BucketId bucket(bucket_bits, superbucket);
            const auto entries = guard->find_parents_and_self(bucket);
            // Entry might not have been written yet. If so, yield to give some time.
            if (entries.empty()) {
                std::this_thread::yield();
                continue;
            }
            ++read_counter;
            // Use plain assertions to avoid any implicit thread/lock interactions with gtest
            assert(entries.size() == 1);
            const auto& entry = entries[0];
            assert(entry.getBucketId() == bucket);
            assert(entry->getNodeCount() == 3);
            // We reuse the same write counter as GC timestamp and checksum/doc count/size across
            // all stored bucket infos in a given bucket.
            const auto expected_stable_val = entry->getLastGarbageCollectionTime();
            for (uint16_t i = 0; i < 3; ++i) {
                const auto& info = entry->getNodeRef(i);
                assert(info.getChecksum()          == expected_stable_val);
                assert(info.getDocumentCount()     == expected_stable_val);
                assert(info.getTotalDocumentSize() == expected_stable_val);
            }
        }
    });
    latch.countDown();
    const auto start_time = vespalib::steady_clock::now();
    uint32_t write_counter = 0;
    do {
        for (uint32_t i = 0; i < n_buckets; ++i, ++write_counter) {
            BucketId bucket_id(bucket_bits, i);
            _db.update(BucketDatabase::Entry(bucket_id, make_bucket_info(write_counter)));
        }
    } while ((vespalib::steady_clock::now() - start_time) < duration);
    run_reader.store(false, std::memory_order_relaxed);
    reader_thread.join();
}

}

