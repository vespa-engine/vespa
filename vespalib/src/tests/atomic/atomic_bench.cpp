// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/fastos/thread.h>
#include <vector>
#include <algorithm>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("atomic_bench");

class Test : public vespalib::TestApp
{
public:
    template<typename C, typename T>
    void testInc(size_t threads, size_t loops);
    int Main() override;
};

template <typename T>
class Changer : public FastOS_Runnable
{
protected:
    volatile T * const _idata;
    const int _times;
public:
    Changer(int times, T *data)
        : _idata(data), _times(times) {}
};

template <typename T>
class Incrementer : public Changer<T>
{
public:
    Incrementer(int times, T *data) : Changer<T>(times, data) {}
    void Run(FastOS_ThreadInterface *, void *) override {
        using vespalib::Atomic;
        for (int i = 0; i < this->_times; ++i) {
            Atomic::postInc(this->_idata);
        }
    }
};

template <typename T>
class IncrementerByCmpSwap : public Changer<T>
{
public:
    IncrementerByCmpSwap(int times, T *data) : Changer<T>(times, data) {}
    void Run(FastOS_ThreadInterface *, void *) override {
        using vespalib::Atomic;
        T oldVal(0);
        for (int i = 0; i < this->_times; ++i) {
            do {
                oldVal = *this->_idata;
            } while ( ! Atomic::cmpSwap(this->_idata, oldVal+1, oldVal));
        }
    }
};

int
Test::Main()
{
    TEST_INIT("atomic_bench");
    size_t concurrency(1);
    size_t numRuns(10000000ul);
    size_t benchType(0);
    if (_argc > 1) {
        benchType = strtoul(_argv[1], NULL, 0);
        if (_argc > 2) {
            numRuns = strtoul(_argv[2], NULL, 0);
            if (_argc > 3) {
                concurrency = strtoul(_argv[3], NULL, 0);
            }
        }
    }
    LOG(info, "Running test number %ld with %ld loops and concurrency of %ld", benchType, numRuns, concurrency);
    if (benchType == 1) {
        testInc<IncrementerByCmpSwap<uint64_t>, uint64_t>(concurrency, numRuns);
    } else {
        testInc<Incrementer<uint64_t>, uint64_t>(concurrency, numRuns);
    }

    TEST_FLUSH();
    TEST_DONE();
}


template<typename C, typename T>
void
Test::testInc(size_t numThreads, size_t loopCount)
{
    std::vector<std::unique_ptr<C>> threads3(numThreads);
    T uintcounter = 0;
    FastOS_ThreadPool tpool3(65000, numThreads);
    for (size_t i = 0; i < numThreads; i++) {
        threads3[i] = std::make_unique<C>(loopCount, &uintcounter);
        tpool3.NewThread(threads3[i].get());
    }
    tpool3.Close();
    EXPECT_TRUE(uintcounter == numThreads * loopCount);
    TEST_FLUSH();
}

TEST_APPHOOK(Test)
