// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

void checkThreads(size_t thread_id, size_t num_threads, std::vector<size_t> &state) {
    if (thread_id == 0) {
        for (size_t i = 0; i < num_threads; ++i) {
            state.push_back(num_threads);
        }
    }
    TEST_BARRIER();
    ASSERT_EQUAL(num_threads, state.size());
    state[thread_id] = thread_id;
    TEST_BARRIER();
    if (thread_id == 0) {
        for (size_t i = 0; i < num_threads; ++i) {
            EXPECT_EQUAL(i, state[i]);
        }
    }
}

TEST_MT("multi-threaded test without fixtures", 100) {
    static std::vector<size_t> state;
    TEST_DO(checkThreads(thread_id, num_threads, state));
}

TEST_MT_F("multi-threaded test with 1 fixture", 100, std::vector<size_t>()) {
    EXPECT_EQUAL(&f, &f1);
    TEST_DO(checkThreads(thread_id, num_threads, f1));
}

TEST_MT_FF("multi-threaded test with 2 fixtures", 100, std::vector<size_t>(), size_t(5)) {
    EXPECT_EQUAL(5u, f2);
    TEST_DO(checkThreads(thread_id, num_threads, f1));
}

TEST_MT_FFF("multi-threaded test with 3 fixtures", 100, std::vector<size_t>(), size_t(5), size_t(10)) {
    EXPECT_EQUAL(5u, f2);
    EXPECT_EQUAL(10u, f3);
    TEST_DO(checkThreads(thread_id, num_threads, f1));
}

TEST_MT_F("let fixture pick up thread count", 14, size_t(num_threads)) {
    EXPECT_EQUAL(num_threads, f1);
}

TEST_MT_FF("let fixtures pick up thread count", 14, size_t(num_threads), size_t(num_threads)) {
    EXPECT_EQUAL(num_threads, f1);
    EXPECT_EQUAL(num_threads, f2);
}

TEST_MT_FFF("let fixturess pick up thread count", 14, size_t(num_threads),
            size_t(num_threads), size_t(num_threads))
{
    EXPECT_EQUAL(num_threads, f1);
    EXPECT_EQUAL(num_threads, f2);
    EXPECT_EQUAL(num_threads, f3);
}

IGNORE_TEST_MT("partial unwind breaks barrier", 10) {
    if (thread_id == 5) {
        TEST_FATAL("partial unwind");
    }
    TEST_BARRIER();
}

IGNORE_TEST_MT("ignore multithreaded test with no fixtures", 10) {
    EXPECT_TRUE(true);
}

IGNORE_TEST_MT_F("ignore multithreaded test with 1 fixture", 10, int(5)) {
    EXPECT_EQUAL(5, f1);
}

IGNORE_TEST_MT_FF("ignore multithreaded test with 2 fixtures", 10, int(5), int(10)) {
    EXPECT_EQUAL(5, f1);
    EXPECT_EQUAL(10, f2);
}

IGNORE_TEST_MT_FFF("ignore multithreaded test with 3 fixtures", 10, int(5), int(10), int(15)) {
    EXPECT_EQUAL(5, f1);
    EXPECT_EQUAL(10, f2);
    EXPECT_EQUAL(15, f3);
}

TEST_MAIN() { TEST_RUN_ALL(); }
