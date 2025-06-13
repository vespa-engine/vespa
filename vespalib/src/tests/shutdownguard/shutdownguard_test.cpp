// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/shutdownguard.h>
#include <vespa/vespalib/util/malloc_mmap_guard.h>
#include <thread>
#include <unistd.h>
#include <sys/wait.h>
#include <cstdlib>

using namespace vespalib;

TEST(ShutdownGuardTest, test_shutdown_guard)
{
    {
        ShutdownGuard farFuture(1000000s);
        std::this_thread::sleep_for(20ms);
    }
    EXPECT_TRUE(true);
    pid_t child = fork();
    if (child == 0) {
        ShutdownGuard soon(30ms);
        for (int i = 0; i < 1000; ++i) {
            std::this_thread::sleep_for(20ms);
        }
        std::_Exit(0);
    }
    for (int i = 0; i < 1000; ++i) {
        std::this_thread::sleep_for(20ms);
        int stat = 0;
        if (waitpid(child, &stat, WNOHANG) == child) {
            EXPECT_TRUE(WIFEXITED(stat));
            EXPECT_EQ(1, WEXITSTATUS(stat));
            break;
        }
        EXPECT_TRUE(i < 800);
    }
}

TEST(ShutdownGuardTest, test_malloc_mmap_guard) {
    MallocMmapGuard guard(0x100000);
}

GTEST_MAIN_RUN_ALL_TESTS()
