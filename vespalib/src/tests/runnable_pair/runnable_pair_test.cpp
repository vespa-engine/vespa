// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
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

TEST("require that runnable pair runs runnables in order") {
    int value = 0;
    Add add(value);
    Mul mul(value);
    RunnablePair pair(add, mul);
    EXPECT_EQUAL(0, value);
    pair.run();
    EXPECT_EQUAL(100, value);
}

TEST_MAIN() { TEST_RUN_ALL(); }
