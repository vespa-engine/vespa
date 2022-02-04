// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <unistd.h>

// Reducing the nice value or setting it directly with setpriority
// requires extra permissions/capabilities. This means that we can use
// nice values to prioritize between threads, but not at task-level
// within a thread.

void set_nice_value(int value, int thread) {
    errno = 0;
    int old_nice = nice(0);
    ASSERT_EQUAL(errno, 0);
    ASSERT_GREATER_EQUAL(value, old_nice);
    int new_nice = nice(value - old_nice);
    ASSERT_EQUAL(errno, 0);
    ASSERT_EQUAL(new_nice, value);
    fprintf(stderr, "nice value for thread %d changed: %d->%d\n", thread, old_nice, new_nice);
}

int get_nice_value(int thread) {
    errno = 0;
    int old_nice = nice(0);
    ASSERT_EQUAL(errno, 0);
    fprintf(stderr, "nice value for thread %d is: %d\n", thread, old_nice);
    return old_nice;
}

int init_nice = get_nice_value(-1);

// Linux has a separate nice value per thread. Note that this is not
// portable and conflicts with the POSIX API. Also note that we can
// only test once since nice values cannot be reduced again and test
// threads are re-used.

TEST_MT("require that nice value is tracked per thread", 5) {
    set_nice_value(init_nice + thread_id, thread_id);
    TEST_BARRIER();
    EXPECT_EQUAL(get_nice_value(thread_id), int(init_nice + thread_id));
}

TEST_MAIN() { TEST_RUN_ALL(); }
