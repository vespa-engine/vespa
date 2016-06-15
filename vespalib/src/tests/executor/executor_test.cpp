// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for executor.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("executor_test");

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/closuretask.h>

using namespace vespalib;

namespace {

class Test : public vespalib::TestApp {
    void requireThatClosuresCanBeWrappedInATask();

public:
    int Main();
};

int
Test::Main()
{
    TEST_INIT("executor_test");

    TEST_DO(requireThatClosuresCanBeWrappedInATask());

    TEST_DONE();
}

void setBool(bool *b) { *b = true; }
void Test::requireThatClosuresCanBeWrappedInATask() {
    bool called = false;
    Executor::Task::UP task = makeTask(makeClosure(setBool, &called));
    EXPECT_TRUE(!called);
    task->run();
    EXPECT_TRUE(called);
}

}  // namespace

TEST_APPHOOK(Test);
