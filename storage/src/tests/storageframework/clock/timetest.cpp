// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/generic/clock/time.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <gtest/gtest.h>

namespace storage::framework::defaultimplementation {

TEST(TimeTest, testBasics)
{
    MicroSecTime timeMicros(1000*1000);

    MicroSecTime timeMicros2 = timeMicros;
    EXPECT_EQ(timeMicros2, timeMicros);
    timeMicros2 = MicroSecTime(timeMicros.getTime() + 25000);
    EXPECT_GT(timeMicros2, timeMicros);
    EXPECT_LT(timeMicros, timeMicros2);
    timeMicros2 = MicroSecTime(timeMicros2.getTime() - 30000);
    EXPECT_LT(timeMicros2, timeMicros);
    EXPECT_GT(timeMicros, timeMicros2);
}

TEST(TimeTest, testCreatedFromClock)
{
    defaultimplementation::FakeClock clock;
    clock.setAbsoluteTimeInSeconds(600);

    EXPECT_EQ(vespalib::system_time(600s), clock.getSystemTime());
    EXPECT_EQ(vespalib::steady_time(600s), clock.getMonotonicTime());
}

TEST(TimeTest, canAssignMicrosecondResolutionTimeToFakeClock)
{
    defaultimplementation::FakeClock clock;
    clock.setAbsoluteTimeInMicroSeconds(1234567); // 1.234567 seconds

    EXPECT_EQ(vespalib::system_time(1234567us), clock.getSystemTime());
    EXPECT_EQ(vespalib::steady_time(1234567us), clock.getMonotonicTime());
    // All non-microsec time points must necessarily be truncated.
}

}
