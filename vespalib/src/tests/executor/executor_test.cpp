// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/executor_stats.h>

using namespace vespalib;

void setBool(bool *b) { *b = true; }

TEST(ExecutorTest, require_that_lambdas_can_be_wrapped_as_tasks) {
    bool called = false;
    Executor::Task::UP task = makeLambdaTask([&called]() { called = true; });
    EXPECT_TRUE(!called);
    task->run();
    EXPECT_TRUE(called);
}

template<typename T>
void verify(const AggregatedAverage<T> & avg, size_t expCount, T expTotal, T expMin, T expMax, double expAvg) {
    EXPECT_EQ(expCount, avg.count());
    EXPECT_EQ(expTotal, avg.total());
    EXPECT_EQ(expMin, avg.min());
    EXPECT_EQ(expMax, avg.max());
    EXPECT_EQ(expAvg, avg.average());
}

TEST(ExecutorTest, test_that_aggregated_averages) {
    GTEST_DO(verify(AggregatedAverage<size_t>(), 0ul, 0ul, std::numeric_limits<size_t>::max(), std::numeric_limits<size_t>::min(), 0.0));
    AggregatedAverage<size_t> avg;
    avg.add(9);
    GTEST_DO(verify(avg, 1ul, 9ul, 9ul, 9ul, 9.0));
    avg.add(8);
    GTEST_DO(verify(avg, 2ul, 17ul, 8ul, 9ul, 8.5));
    avg.add(3, 17, 4,17);
    GTEST_DO(verify(avg, 5ul, 34ul, 4ul, 17ul, 6.8));
    AggregatedAverage<size_t> avg2;
    avg2.add(avg);
    GTEST_DO(verify(avg2, 5ul, 34ul, 4ul, 17ul, 6.8));
    avg2 += avg;
    GTEST_DO(verify(avg2, 10ul, 68ul, 4ul, 17ul, 6.8));
}

GTEST_MAIN_RUN_ALL_TESTS()
