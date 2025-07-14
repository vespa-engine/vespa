// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <atomic>
#include <unistd.h>

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
        if (pthread_mutex_init(&_mutex, nullptr) != 0) { abort(); }
        if (pthread_cond_init(&_cond, nullptr) != 0) { abort(); }
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

int my_argc = 0;
char **my_argv = nullptr;

TEST(ThreadTest, main) {
    size_t threadCount(102400);
    if (my_argc >= 3) {
        threadCount = strtoul(my_argv[2], nullptr, 0);
    }

    const char * testType = my_argv[1];

    for (size_t i(0); i < threadCount; i++) {    
        pthread_t th;
        void *retval;
        if (strcmp(testType, "exit") == 0) {
            EXPECT_EQ( pthread_create(&th, nullptr, just_exit, nullptr), 0);
        } else if (strcmp(testType, "cancel") == 0) {
            EXPECT_EQ( pthread_create(&th, nullptr, just_cancel, nullptr), 0);
            EXPECT_EQ( pthread_cancel(th), 0);
        } else {
            EXPECT_EQ( pthread_create(&th, nullptr, just_return, nullptr), 0);
        }
        EXPECT_EQ(pthread_join(th, &retval), 0);
    }

    wait_info info;
    pthread_attr_t attr;
    EXPECT_EQ(pthread_attr_init(&attr), 0);
    EXPECT_EQ(pthread_attr_setstacksize(&attr, 64*1024), 0);
    EXPECT_EQ(info._count, 0ul);
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
    EXPECT_EQ( pthread_create(&th, &attr, just_wait, &info), EAGAIN); // Verify that you have reached upper limit of threads with vespamalloc.
    while (info._count != NUM_THREADS) {
        usleep(1);
    }
    pthread_mutex_lock(&info._mutex);
    pthread_cond_signal(&info._cond);
    pthread_mutex_unlock(&info._mutex);
    for (size_t j=0;j < NUM_THREADS;j++) {
        void *retval;
        EXPECT_EQ(pthread_join(tl[j], &retval), 0);
    }
    EXPECT_EQ(pthread_attr_destroy(&attr), 0);
    EXPECT_EQ(info._count, 0ul);
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    my_argc = argc;
    my_argv = argv;
    return RUN_ALL_TESTS();
}
