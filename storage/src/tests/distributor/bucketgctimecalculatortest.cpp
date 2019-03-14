// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <chrono>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/bucketgctimecalculator.h>

namespace storage {
namespace distributor {

struct MockBucketIdHasher : public BucketGcTimeCalculator::BucketIdHasher
{
    size_t nextGeneratedHash {0};

    size_t doHash(const document::BucketId&) const override {
        return nextGeneratedHash;
    }
};

struct BucketGcTimeCalculatorTest : public CppUnit::TestFixture
{
    void noGcIfAlreadyCheckedAfterStartPoint();
    void gcIfNotRunInCurrentPeriodAndCheckPeriodPassed();
    void noGcIfNotRunInCurrentPeriodAndCheckPeriodNotPassed();
    void noGcIfCheckIntervalIsZero();
    void identityHasherReturnsBucketId();

    BucketGcTimeCalculatorTest();

    CPPUNIT_TEST_SUITE(BucketGcTimeCalculatorTest);
    CPPUNIT_TEST(noGcIfAlreadyCheckedAfterStartPoint);
    CPPUNIT_TEST(gcIfNotRunInCurrentPeriodAndCheckPeriodPassed);
    CPPUNIT_TEST(noGcIfNotRunInCurrentPeriodAndCheckPeriodNotPassed);
    CPPUNIT_TEST(noGcIfCheckIntervalIsZero);
    CPPUNIT_TEST(identityHasherReturnsBucketId);
    CPPUNIT_TEST_SUITE_END();

private:
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

CPPUNIT_TEST_SUITE_REGISTRATION(BucketGcTimeCalculatorTest);

void
BucketGcTimeCalculatorTest::noGcIfAlreadyCheckedAfterStartPoint()
{
    // Note: LastRun(0) is considered to be within the current period.
    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(0), LastRunAt(0)));
    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(499), LastRunAt(0)));
    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(999), LastRunAt(500)));

    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(1000), LastRunAt(1000)));
    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(1234), LastRunAt(1100)));
    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(1600), LastRunAt(1500)));
}

void
BucketGcTimeCalculatorTest::gcIfNotRunInCurrentPeriodAndCheckPeriodPassed()
{
    CPPUNIT_ASSERT(calc.shouldGc(b, CurrentTime(500), LastRunAt(0)));
    CPPUNIT_ASSERT(calc.shouldGc(b, CurrentTime(1600), LastRunAt(500)));
    // Note: this may look wrong, but is correct since GC should have been
    // scheduled _after_ 1499 so this is most likely the case where a bucket
    // has been added to the database at this point in time. Not treating
    // this as a valid GC scenario would mean newly added buckets would have to
    // wait until the next period to be considered. If the period is long and
    // the system is unstable (causing many bucket handoffs), we'd risk not
    // being able to scheduled many buckets at all.
    CPPUNIT_ASSERT(calc.shouldGc(b, CurrentTime(1600), LastRunAt(1499)));

    CPPUNIT_ASSERT(calc.shouldGc(b, CurrentTime(2000), LastRunAt(500)));
    CPPUNIT_ASSERT(calc.shouldGc(b, CurrentTime(2600), LastRunAt(1500)));
}

void
BucketGcTimeCalculatorTest::noGcIfNotRunInCurrentPeriodAndCheckPeriodNotPassed()
{
    CPPUNIT_ASSERT(!calc.shouldGc(b, CurrentTime(1000), LastRunAt(500)));
}

void
BucketGcTimeCalculatorTest::noGcIfCheckIntervalIsZero()
{
    BucketGcTimeCalculator calc2(hasher, std::chrono::seconds(0));
    CPPUNIT_ASSERT(!calc2.shouldGc(b, CurrentTime(5000), LastRunAt(0)));
}

void
BucketGcTimeCalculatorTest::identityHasherReturnsBucketId()
{
    BucketGcTimeCalculator::BucketIdIdentityHasher hasher2;
    document::BucketId bucket(36, 1234);

    CPPUNIT_ASSERT_EQUAL(bucket.getId(), static_cast<uint64_t>(hasher2.hash(bucket)));
}

} // distributor
} // storage

