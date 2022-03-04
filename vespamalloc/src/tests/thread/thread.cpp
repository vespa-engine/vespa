// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <atomic>
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

void * just_return(void * arg)
{
    return arg;
}

void * just_exit(void * arg)
{
    pthread_exit(arg);
}

void * just_cancel(void * arg)
{
    sleep(60);
    return arg;
}

struct wait_info {
    wait_info() :
        _count(0)
    {
        if (pthread_mutex_init(&_mutex, NULL) != 0) { abort(); }
        if (pthread_cond_init(&_cond, NULL) != 0) { abort(); }
    }
    ~wait_info() {
        if (pthread_mutex_destroy(&_mutex) != 0) { abort(); }
        if (pthread_cond_destroy(&_cond) != 0) { abort(); }
    }
    pthread_cond_t    _cond;
    pthread_mutex_t   _mutex;
    std::atomic<uint64_t> _count;
};

void * just_wait(void * arg)
{
    wait_info * info = (wait_info *) arg;
    pthread_mutex_lock(&info->_mutex);
    info->_count.fetch_add(1);
    pthread_cond_wait(&info->_cond, &info->_mutex);
    pthread_mutex_unlock(&info->_mutex);
    pthread_cond_signal(&info->_cond);
    info->_count.fetch_sub(1);
    return arg;
}

int Test::Main()
{
    TEST_INIT("thread_test");
    size_t threadCount(102400);
    if (_argc >= 3) {
        threadCount = strtoul(_argv[2], NULL, 0);
    }

    const char * testType = _argv[1];

    for (size_t i(0); i < threadCount; i++) {    
        pthread_t th;
        void *retval;
        if (strcmp(testType, "exit") == 0) {
            EXPECT_EQUAL( pthread_create(&th, NULL, just_exit, NULL), 0);
        } else if (strcmp(testType, "cancel") == 0) {
            EXPECT_EQUAL( pthread_create(&th, NULL, just_cancel, NULL), 0);
            EXPECT_EQUAL( pthread_cancel(th), 0);
        } else {
            EXPECT_EQUAL( pthread_create(&th, NULL, just_return, NULL), 0);
        }
        EXPECT_EQUAL(pthread_join(th, &retval), 0);
    }

    wait_info info;
    pthread_attr_t attr;
    EXPECT_EQUAL(pthread_attr_init(&attr), 0);
    EXPECT_EQUAL(pthread_attr_setstacksize(&attr, 64*1024), 0);
    EXPECT_EQUAL(info._count, 0ul);
    const size_t NUM_THREADS(16382); // +1 for main thread, +1 for testsystem = 16384
    pthread_t tl[NUM_THREADS];
    for (size_t j=0;j < NUM_THREADS;j++) {
        int e = pthread_create(&tl[j], &attr, just_wait, &info);
        if (e != 0) {
            fprintf(stderr, "pthread_create failed at index '%ld'. with errno='%d'", j, e);
            perror("pthread_create failed");
            abort();
        }
    }
    pthread_t th;
    EXPECT_EQUAL( pthread_create(&th, &attr, just_wait, &info), EAGAIN); // Verify that you have reached upper limit of threads with vespamalloc.
    while (info._count != NUM_THREADS) {
        usleep(1);
    }
    pthread_mutex_lock(&info._mutex);
    pthread_cond_signal(&info._cond);
    pthread_mutex_unlock(&info._mutex);
    for (size_t j=0;j < NUM_THREADS;j++) {
        void *retval;
        EXPECT_EQUAL(pthread_join(tl[j], &retval), 0);
    }
    EXPECT_EQUAL(pthread_attr_destroy(&attr), 0);
    EXPECT_EQUAL(info._count, 0ul);
    TEST_DONE();
}

TEST_APPHOOK(Test);
