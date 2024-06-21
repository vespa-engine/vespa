// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <unistd.h>
#include <cassert>

using namespace vespalib;

void * hammer(void * arg)
{
    usleep(4000000);
    long seconds = * static_cast<const long *>(arg);
    long stopTime(time(nullptr) + seconds);
    pthread_t id = pthread_self();
    while (time(nullptr) < stopTime) {
         std::vector<pthread_t *> allocations;
         for (size_t i(0); i < 2000; i++) {
             pthread_t *t = new pthread_t[20];
             allocations.push_back(t);
             for (size_t j(0); j < 20; j++) {
                 t[j] = id;
             }
         }

         for (auto & allocation : allocations) {
             for (size_t j(0); j < 20; j++) {
                 assert(allocation[j] == id);
             }
             delete [] allocation;
         }
    }
    return arg;
}

TEST_MAIN() {
    size_t threadCount(1024);
    long seconds(10);
    if (argc >= 2) {
        threadCount = strtoul(argv[1], nullptr, 0);
        if (argc >= 3) {
            seconds = strtoul(argv[2], nullptr, 0);
        }
    }

    pthread_attr_t attr;
    EXPECT_EQUAL(pthread_attr_init(&attr), 0);
    EXPECT_EQUAL(pthread_attr_setstacksize(&attr, 64*1024), 0);
    std::vector<pthread_t> threads(threadCount);
    for (size_t i(0); i < threadCount; i++) {    
        EXPECT_EQUAL( pthread_create(&threads[i], &attr, hammer, &seconds), 0);
    }
    for (size_t i(0); i < threadCount; i++) {    
        void *retval;
        EXPECT_EQUAL(pthread_join(threads[i], &retval), 0);
    }
}
