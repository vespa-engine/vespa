// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/lambdatask.h>

using namespace vespalib;

void setBool(bool *b) { *b = true; }

TEST("require that closures can be wrapped as tasks") {
    bool called = false;
    Executor::Task::UP task = makeTask(makeClosure(setBool, &called));
    EXPECT_TRUE(!called);
    task->run();
    EXPECT_TRUE(called);
}

TEST("require that lambdas can be wrapped as tasks") {
    bool called = false;
    Executor::Task::UP task = makeLambdaTask([&called]() { called = true; });
    EXPECT_TRUE(!called);
    task->run();
    EXPECT_TRUE(called);
}

TEST_MAIN() { TEST_RUN_ALL(); }
