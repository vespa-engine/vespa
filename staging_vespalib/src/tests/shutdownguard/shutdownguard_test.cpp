// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/shutdownguard.h>

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("shutdownguard_test");
    {
        ShutdownGuard farFuture(123456789);
        FastOS_Thread::Sleep(20);
    }
    EXPECT_TRUE(true);
    pid_t child = fork();
    if (child == 0) {
        ShutdownGuard soon(30);
        for (int i = 0; i < 1000; ++i) {
            FastOS_Thread::Sleep(20);
        }
        exit(0);
    }
    for (int i = 0; i < 1000; ++i) {
        FastOS_Thread::Sleep(20);
        int stat = 0;
        if (waitpid(child, &stat, WNOHANG) == child) {
            EXPECT_TRUE(WIFEXITED(stat));
            EXPECT_EQUAL(1, WEXITSTATUS(stat));
            break;
        }
        EXPECT_TRUE(i < 800);
    }
    TEST_DONE();
}
