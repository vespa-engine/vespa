// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/barrier.h>

using namespace vespalib;

struct Fixture {
    Barrier barrier;
    CountDownLatch latch;
    Fixture(size_t n) : barrier(n), latch(n) {}
};

TEST_MT_F("require that barriers are satisfied by the appropriate number of threads", 3, Fixture(num_threads)) {
    if (thread_id == 0) {
        f1.latch.countDown();
        EXPECT_FALSE(f.latch.await(250ms));
        EXPECT_TRUE(f.barrier.await());
        EXPECT_TRUE(f.latch.await(25s));
    } else {
        EXPECT_TRUE(f1.barrier.await());
        f1.latch.countDown();
    }
}

TEST_MT_F("require that barriers can be used multiple times", 3, Fixture(num_threads)) {
    EXPECT_TRUE(f1.barrier.await());
    EXPECT_TRUE(f1.barrier.await());
    if (thread_id == 0) {
        f1.latch.countDown();
        EXPECT_FALSE(f.latch.await(250ms));
        EXPECT_TRUE(f.barrier.await());
        EXPECT_TRUE(f.latch.await(25s));
    } else {
        EXPECT_TRUE(f1.barrier.await());
        f1.latch.countDown();
    }
}

TEST_MT_F("require that barriers can be broken", 3, Fixture(num_threads)) {
    EXPECT_TRUE(f1.barrier.await());
    if (thread_id == 0) {
        f1.latch.countDown();
        EXPECT_FALSE(f.latch.await(250ms));
        f1.barrier.destroy();
        EXPECT_TRUE(f.latch.await(25s));
    } else {
        EXPECT_FALSE(f1.barrier.await());
        f1.latch.countDown();
    }
    EXPECT_FALSE(f1.barrier.await());
}

TEST_MT_F("require that barriers cannot be retroactively broken", 100, Barrier(num_threads)) {
    EXPECT_TRUE(f1.await());
    f1.destroy();
    EXPECT_FALSE(f1.await());
}

TEST_MAIN() { TEST_RUN_ALL(); }
