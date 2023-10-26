// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

TEST_MT_F("multiple threads", 2, std::vector<size_t>(num_threads)) {
    ASSERT_EQUAL(num_threads, f1.size());
    f1[thread_id] = thread_id;
    TEST_BARRIER();
    if (thread_id == 0) {
        TEST_TRACE();
        EXPECT_EQUAL(1u, f1[1]);
    } else {
        TEST_TRACE();
        EXPECT_EQUAL(0u, f1[0]);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
