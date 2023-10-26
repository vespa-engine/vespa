// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("creatingmanythreads_test");

TEST_SETUP(Test);

void * thread_alloc(void * arg)
{
    char * v = new char [*static_cast<int *>(arg)];
    delete [] v;
    return NULL;
}

int Test::Main() {
    int numThreads(10000);
    int allocSize(256);
    if (_argc > 1) {
        numThreads = atoi(_argv[1]);
    }
    if (_argc > 2) {
        allocSize = atoi(_argv[2]);
    }
    TEST_INIT("creatingmanythreads_test");

    LOG(info, "Will create and run %d threads each allocating a single block of memory of %d size\n", numThreads, allocSize);
    for (int i(0); i < numThreads; ) {
        for (int j(0); (i < numThreads) && j < 10000; i++, j++) {
            pthread_t thread;
            ASSERT_EQUAL(0, pthread_create(&thread, NULL, thread_alloc, &allocSize));
            ASSERT_EQUAL(0, pthread_join(thread, NULL));
        }
        LOG(info, "Completed %d tests", i);
    }

    TEST_DONE();
}
