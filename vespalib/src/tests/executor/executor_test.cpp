// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/executor_stats.h>

using namespace vespalib;

void setBool(bool *b) { *b = true; }

TEST("require that lambdas can be wrapped as tasks") {
    bool called = false;
    Executor::Task::UP task = makeLambdaTask([&called]() { called = true; });
    EXPECT_TRUE(!called);
    task->run();
    EXPECT_TRUE(called);
}

template<typename T>
void verify(const AggregatedAverage<T> & avg, size_t expCount, T expTotal, T expMin, T expMax, double expAvg) {
    EXPECT_EQUAL(expCount, avg.count());
    EXPECT_EQUAL(expTotal, avg.total());
    EXPECT_EQUAL(expMin, avg.min());
    EXPECT_EQUAL(expMax, avg.max());
    EXPECT_EQUAL(expAvg, avg.average());
}

TEST("test that aggregated averages") {
    TEST_DO(verify(AggregatedAverage<size_t>(), 0ul, 0ul, std::numeric_limits<size_t>::max(), std::numeric_limits<size_t>::min(), 0.0));
    AggregatedAverage<size_t> avg;
    avg.add(9);
    TEST_DO(verify(avg, 1ul, 9ul, 9ul, 9ul, 9.0));
    avg.add(8);
    TEST_DO(verify(avg, 2ul, 17ul, 8ul, 9ul, 8.5));
    avg.add(3, 17, 4,17);
    TEST_DO(verify(avg, 5ul, 34ul, 4ul, 17ul, 6.8));
    AggregatedAverage<size_t> avg2;
    avg2.add(avg);
    TEST_DO(verify(avg2, 5ul, 34ul, 4ul, 17ul, 6.8));
    avg2 += avg;
    TEST_DO(verify(avg2, 10ul, 68ul, 4ul, 17ul, 6.8));
}

TEST_MAIN() { TEST_RUN_ALL(); }
