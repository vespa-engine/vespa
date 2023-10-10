// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <chrono>
#include <vespa/storage/distributor/bucketgctimecalculator.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage::distributor {

struct MockBucketIdHasher : public BucketGcTimeCalculator::BucketIdHasher
{
    size_t nextGeneratedHash {0};

    size_t doHash(const document::BucketId&) const noexcept override {
        return nextGeneratedHash;
    }
};

struct BucketGcTimeCalculatorTest : Test {
    BucketGcTimeCalculatorTest();

    // Ease of reading aliases
    using CurrentTime = std::chrono::seconds;
    using LastRunAt = std::chrono::seconds;

    MockBucketIdHasher hasher;
    std::chrono::seconds checkInterval;
    BucketGcTimeCalculator calc;
    document::BucketId b;
};

BucketGcTimeCalculatorTest::BucketGcTimeCalculatorTest()
    : checkInterval(1000),
      calc(hasher, checkInterval),
      b(16, 1)
{
    hasher.nextGeneratedHash = 500;
}

TEST_F(BucketGcTimeCalculatorTest, no_gc_if_already_checked_after_start_point) {
    // Note: LastRun(0) is considered to be within the current period.
    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(0), LastRunAt(0)));
    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(499), LastRunAt(0)));
    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(999), LastRunAt(500)));

    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(1000), LastRunAt(1000)));
    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(1234), LastRunAt(1100)));
    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(1600), LastRunAt(1500)));
}

TEST_F(BucketGcTimeCalculatorTest, gc_if_not_run_in_current_period_and_check_period_passed) {
    EXPECT_TRUE(calc.shouldGc(b, CurrentTime(500), LastRunAt(0)));
    EXPECT_TRUE(calc.shouldGc(b, CurrentTime(1600), LastRunAt(500)));
    // Note: this may look wrong, but is correct since GC should have been
    // scheduled _after_ 1499 so this is most likely the case where a bucket
    // has been added to the database at this point in time. Not treating
    // this as a valid GC scenario would mean newly added buckets would have to
    // wait until the next period to be considered. If the period is long and
    // the system is unstable (causing many bucket handoffs), we'd risk not
    // being able to schedule many buckets at all.
    EXPECT_TRUE(calc.shouldGc(b, CurrentTime(1600), LastRunAt(1499)));

    EXPECT_TRUE(calc.shouldGc(b, CurrentTime(2000), LastRunAt(500)));
    EXPECT_TRUE(calc.shouldGc(b, CurrentTime(2600), LastRunAt(1500)));
}

TEST_F(BucketGcTimeCalculatorTest, no_gc_if_not_run_in_current_period_and_check_period_not_passed) {
    EXPECT_FALSE(calc.shouldGc(b, CurrentTime(1000), LastRunAt(500)));
}

TEST_F(BucketGcTimeCalculatorTest, no_gc_if_check_interval_is_zero) {
    BucketGcTimeCalculator calc2(hasher, std::chrono::seconds(0));
    EXPECT_FALSE(calc2.shouldGc(b, CurrentTime(5000), LastRunAt(0)));
}

TEST_F(BucketGcTimeCalculatorTest, identity_hasher_returns_bucket_id) {
    BucketGcTimeCalculator::BucketIdIdentityHasher hasher2;
    document::BucketId bucket(36, 1234);

    EXPECT_EQ(bucket.getId(), static_cast<uint64_t>(hasher2.hash(bucket)));
}

} // storage::distributor
