// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespamalloc/malloc/allocchunk.h>

class Test : public vespalib::TestApp
{
public:
    int Main() override;
private:
    template<typename T>
    void testSwap(T initial);
    template<typename T>
    void testSwapStress(T v, int numThreads);
};

template <typename T>
class Stress : public FastOS_Runnable
{
private:
    void Run(FastOS_ThreadInterface * ti, void * arg) override;
    void stressSwap(T & value);
public:
    Stress(T * value) : _value(value), _successCount(0), _failedCount(0) { }
    void wait() { _wait.Lock(); _wait.Unlock(); }
    FastOS_Mutex _wait;
    T      * _value;
    size_t   _successCount;
    size_t   _failedCount;
};

TEST_APPHOOK(Test);

template<typename T>
void Test::testSwap(T initial)
{
    T value(initial);

    ASSERT_TRUE(vespalib::Atomic::cmpSwap(&value, initial+1, initial));
    ASSERT_TRUE(value == initial+1);

    ASSERT_TRUE(!vespalib::Atomic::cmpSwap(&value, initial+2, initial));
    ASSERT_TRUE(value == initial+1);
}

template<typename T>
void Test::testSwapStress(T v, int numThreads)
{
    T old(v);
    std::vector<Stress<T> *> contexts;
    std::vector<FastOS_ThreadInterface *> threads;
    FastOS_ThreadPool threadPool(512*1024);

    for(int i=0; i < numThreads; i++) {
        contexts.push_back(new Stress<T>(&v));
    }

    for(size_t i = 0; i < contexts.size(); i++) {
        threads.push_back(threadPool.NewThread(contexts[i]));
    }
    FastOS_Thread::Sleep(1000);
    size_t succesCount(0);
    size_t failedCount(0);
    for(size_t i = 0; i < contexts.size(); i++) {
        Stress<T> * s = contexts[i];
        s->wait();
        succesCount += s->_successCount;
        failedCount += s->_failedCount;
    }
    ASSERT_TRUE(v == 0);
    ASSERT_TRUE(old == succesCount);
    fprintf(stderr, "%ld threads counting down from %" PRIu64 " had %ld succesfull and %ld unsuccessful attempts\n",
            contexts.size(), uint64_t(old), succesCount, failedCount);
    for(size_t i = 0; i < contexts.size(); i++) {
        delete contexts[i];
    }
}

template <typename T>
void Stress<T>::Run(FastOS_ThreadInterface *, void *)
{
    _wait.Lock();
    stressSwap(*_value);
    _wait.Unlock();
}

template <typename T>
void Stress<T>::stressSwap(T & value)
{
    for (T old = value; old > 0; old = value) {
        if (vespalib::Atomic::cmpSwap(&value, old-1, old)) {
            _successCount++;
        } else {
            _failedCount++;
        }
    }
}

int Test::Main()
{
    TEST_INIT("atomic");

    {
        std::atomic<uint32_t> uint32V;
        ASSERT_TRUE(uint32V.is_lock_free());
    }
    {
        std::atomic<uint64_t> uint64V;
        ASSERT_TRUE(uint64V.is_lock_free());
    }
    {
        std::atomic<vespamalloc::TaggedPtr> taggedPtr;
        ASSERT_EQUAL(16, sizeof(vespamalloc::TaggedPtr));
        ASSERT_TRUE(taggedPtr.is_lock_free());
    }

    testSwap<uint32_t>(6);
    testSwap<uint32_t>(7);
    testSwap<uint32_t>(uint32_t(-6));
    testSwap<uint32_t>(uint32_t(-7));
    testSwap<uint64_t>(6);
    testSwap<uint64_t>(7);
    testSwap<uint64_t>(uint64_t(-6));
    testSwap<uint64_t>(uint64_t(-7));
    testSwapStress<uint64_t>(0x1000000, 4);
    testSwapStress<uint32_t>(0x1000000, 4);

    TEST_DONE();
}
