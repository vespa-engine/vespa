// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <unistd.h>

using namespace vespalib;

class Test : public TestApp
{
public:
    ~Test();
    int Main() override;
};

Test::~Test()
{
}

void * hammer(void * arg)
{
    usleep(4000000);
    long seconds = * static_cast<const long *>(arg);
    long stopTime(time(NULL) + seconds);
    pthread_t id = pthread_self();
    while (time(NULL) < stopTime) {
         std::vector<pthread_t *> allocations;
         for (size_t i(0); i < 2000; i++) {
             pthread_t *t = new pthread_t[20];
             allocations.push_back(t);
             for (size_t j(0); j < 20; j++) {
                 t[j] = id;
             }
         }

         for (size_t i(0); i < allocations.size(); i++) {
             for (size_t j(0); j < 20; j++) {
                 assert(allocations[i][j] == id);
             }
             delete [] allocations[i];
         }
    }
    return arg;
}

int Test::Main()
{
    TEST_INIT("racemanythreads_test");
    size_t threadCount(1024);
    long seconds(10);
    if (_argc >= 2) {
        threadCount = strtoul(_argv[1], NULL, 0);
        if (_argc >= 3) {
            seconds = strtoul(_argv[2], NULL, 0);
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

    TEST_DONE();
}

TEST_APPHOOK(Test);
