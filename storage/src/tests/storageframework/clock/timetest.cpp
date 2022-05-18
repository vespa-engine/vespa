// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/generic/clock/time.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <gtest/gtest.h>

namespace storage::framework::defaultimplementation {

TEST(TimeTest, testBasics)
{
    SecondTime timeSec(1);

    MilliSecTime timeMillis = timeSec.getMillis();
    EXPECT_EQ(uint64_t(1000), timeMillis.getTime());
    EXPECT_EQ(timeSec, timeMillis.getSeconds());

    MicroSecTime timeMicros = timeSec.getMicros();
    EXPECT_EQ(timeSec.getMicros(), timeMillis.getMicros());
    EXPECT_EQ(timeMillis, timeMicros.getMillis());
    EXPECT_EQ(timeSec, timeMicros.getSeconds());

    MicroSecTime timeMicros2 = timeMicros;
    EXPECT_EQ(timeMicros2, timeMicros);
    timeMicros2 += MicroSecTime(25000);
    EXPECT_GT(timeMicros2, timeMicros);
    EXPECT_LT(timeMicros, timeMicros2);
    timeMicros2 -= MicroSecTime(30000);
    EXPECT_LT(timeMicros2, timeMicros);
    EXPECT_GT(timeMicros, timeMicros2);
    timeMicros2 += MicroSecTime(55000);

    MilliSecTime timeMillis2 = timeMicros2.getMillis();
    EXPECT_GT(timeMillis2, timeMillis);
    EXPECT_EQ(uint64_t(1050), timeMillis2.getTime());
    EXPECT_EQ(timeSec, timeMillis2.getSeconds());
}

TEST(TimeTest, testCreatedFromClock)
{
    defaultimplementation::FakeClock clock;
    clock.setAbsoluteTimeInSeconds(600);

    EXPECT_EQ(SecondTime(600), SecondTime(clock));
    EXPECT_EQ(MilliSecTime(600 * 1000), MilliSecTime(clock));
    EXPECT_EQ(MicroSecTime(600 * 1000 * 1000), MicroSecTime(clock));
}

TEST(TimeTest, canAssignMicrosecondResolutionTimeToFakeClock)
{
    defaultimplementation::FakeClock clock;
    clock.setAbsoluteTimeInMicroSeconds(1234567); // 1.234567 seconds

    // All non-microsec time points must necessarily be truncated.
    EXPECT_EQ(SecondTime(1), SecondTime(clock));
    EXPECT_EQ(MilliSecTime(1234), MilliSecTime(clock));
    EXPECT_EQ(MicroSecTime(1234567), MicroSecTime(clock));
}

}
