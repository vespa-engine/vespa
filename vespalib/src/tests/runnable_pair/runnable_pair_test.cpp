// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/runnable_pair.h>

using namespace vespalib;

struct Add : public Runnable {
    int &val;
    Add(int &v) : val(v) {}
    void run() override { val += 10; }
};

struct Mul : public Runnable {
    int &val;
    Mul(int &v) : val(v) {}
    void run() override { val *= 10; }
};

TEST(RunnablePairTest, require_that_runnable_pair_runs_runnables_in_order) {
    int value = 0;
    Add add(value);
    Mul mul(value);
    RunnablePair pair(add, mul);
    EXPECT_EQ(0, value);
    pair.run();
    EXPECT_EQ(100, value);
}

GTEST_MAIN_RUN_ALL_TESTS()
