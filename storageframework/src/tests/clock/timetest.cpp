// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/generic/clock/time.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct TimeTest : public CppUnit::TestFixture
{
    void testBasics();
    void testCreatedFromClock();
    void canAssignMicrosecondResolutionTimeToFakeClock();

    CPPUNIT_TEST_SUITE(TimeTest);
    CPPUNIT_TEST(testBasics); // Fails sometimes, test needs rewrite.
    CPPUNIT_TEST(testCreatedFromClock);
    CPPUNIT_TEST(canAssignMicrosecondResolutionTimeToFakeClock);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(TimeTest);

void
TimeTest::testBasics()
{
    SecondTime timeSec(1);

    MilliSecTime timeMillis = timeSec.getMillis();
    CPPUNIT_ASSERT_EQUAL(uint64_t(1000), timeMillis.getTime());
    CPPUNIT_ASSERT_EQUAL(timeSec, timeMillis.getSeconds());

    MicroSecTime timeMicros = timeSec.getMicros();
    CPPUNIT_ASSERT_EQUAL(timeSec.getMicros(), timeMillis.getMicros());
    CPPUNIT_ASSERT_EQUAL(timeMillis, timeMicros.getMillis());
    CPPUNIT_ASSERT_EQUAL(timeSec, timeMicros.getSeconds());

    MicroSecTime timeMicros2 = timeMicros;
    CPPUNIT_ASSERT(timeMicros2 == timeMicros);
    timeMicros2 += MicroSecTime(25000);
    CPPUNIT_ASSERT(timeMicros2 > timeMicros);
    CPPUNIT_ASSERT(timeMicros < timeMicros2);
    timeMicros2 -= MicroSecTime(30000);
    CPPUNIT_ASSERT(timeMicros2 < timeMicros);
    CPPUNIT_ASSERT(timeMicros > timeMicros2);
    timeMicros2 += MicroSecTime(55000);

    MilliSecTime timeMillis2 = timeMicros2.getMillis();
    CPPUNIT_ASSERT(timeMillis2 > timeMillis);
    CPPUNIT_ASSERT_EQUAL(uint64_t(1050), timeMillis2.getTime());
    CPPUNIT_ASSERT_EQUAL(timeSec, timeMillis2.getSeconds());
}

void
TimeTest::testCreatedFromClock()
{
    defaultimplementation::FakeClock clock;
    clock.setAbsoluteTimeInSeconds(600);

    CPPUNIT_ASSERT_EQUAL(SecondTime(600), SecondTime(clock));
    CPPUNIT_ASSERT_EQUAL(MilliSecTime(600 * 1000), MilliSecTime(clock));
    CPPUNIT_ASSERT_EQUAL(MicroSecTime(600 * 1000 * 1000), MicroSecTime(clock));
}

void
TimeTest::canAssignMicrosecondResolutionTimeToFakeClock()
{
    defaultimplementation::FakeClock clock;
    clock.setAbsoluteTimeInMicroSeconds(1234567); // 1.234567 seconds

    // All non-microsec time points must necessarily be truncated.
    CPPUNIT_ASSERT_EQUAL(SecondTime(1), SecondTime(clock));
    CPPUNIT_ASSERT_EQUAL(MilliSecTime(1234), MilliSecTime(clock));
    CPPUNIT_ASSERT_EQUAL(MicroSecTime(1234567), MicroSecTime(clock));
}

} // defaultimplementation
} // framework
} // storage
