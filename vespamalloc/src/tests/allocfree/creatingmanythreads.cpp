// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP("creatingmanythreads_test");

void * thread_alloc(void * arg)
{
    char * v = new char [*static_cast<int *>(arg)];
    delete [] v;
    return NULL;
}

TEST_MAIN() {
    int numThreads(10000);
    int allocSize(256);
    if (argc > 1) {
        numThreads = atoi(argv[1]);
    }
    if (argc > 2) {
        allocSize = atoi(argv[2]);
    }

    LOG(info, "Will create and run %d threads each allocating a single block of memory of %d size\n", numThreads, allocSize);
    for (int i(0); i < numThreads; ) {
        for (int j(0); (i < numThreads) && j < 10000; i++, j++) {
            pthread_t thread;
            ASSERT_EQUAL(0, pthread_create(&thread, NULL, thread_alloc, &allocSize));
            ASSERT_EQUAL(0, pthread_join(thread, NULL));
        }
        LOG(info, "Completed %d tests", i);
    }
}
