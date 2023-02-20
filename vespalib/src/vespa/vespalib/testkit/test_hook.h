// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/util/thread.h>
#include <thread>
#include <string>
#include <vector>
#include <cassert>
#include "test_master.h"

namespace vespalib {

struct TestThreadEntry {
    virtual void threadEntry() = 0;
    virtual ~TestThreadEntry() {}
};

struct TestFixtureWrapper {
    size_t thread_id;
    size_t num_threads;
    TestFixtureWrapper() : thread_id(0), num_threads(1) {}
    virtual void test_entry_point() = 0;
    virtual ~TestFixtureWrapper() {}
};

class TestThreadWrapper : public TestThreadEntry
{
private:
    bool                                      _result;
    bool                                      _ignore;
    CountDownLatch                           &_latch;
    Barrier                                  &_barrier;
    const std::vector<TestMaster::TraceItem> &_traceStack;
    TestFixtureWrapper                       &_fixture;

public:
    TestThreadWrapper(bool ignore, CountDownLatch &l, Barrier &b,
                      const std::vector<TestMaster::TraceItem> &traceStack,
                      TestFixtureWrapper &fixture)
        : _result(false), _ignore(ignore),
          _latch(l), _barrier(b), _traceStack(traceStack),
          _fixture(fixture) {}

    void threadEntry() override;
    bool getResult() const {
        return _result;
    }
};

#ifndef IAM_DOXYGEN
class TestHook
{
private:
    static TestHook *_head;
    static TestHook *_tail;
    TestHook        *_next;
    std::string      _name;
    std::string      _tag;
    bool             _ignore;

    TestHook(const TestHook &);
    TestHook &operator=(const TestHook &);

protected:
    TestHook(const std::string &file, const std::string &name, bool ignore);
    virtual ~TestHook() {}

    template <typename T>
    bool runTest(const T &fixture, size_t num_threads) {
        assert(num_threads > 0);
        using ThreadUP = std::unique_ptr<TestThreadWrapper>;
        using FixtureUP = std::unique_ptr<T>;
        std::vector<TestMaster::TraceItem> traceStack = TestMaster::master.getThreadTraceStack();
        CountDownLatch latch(num_threads);
        Barrier barrier(num_threads);
        std::vector<FixtureUP> fixtures;
        std::vector<ThreadUP> threads;
        ThreadPool pool;
        threads.reserve(num_threads);
        fixtures.reserve(num_threads);
        for (size_t i = 0; i < num_threads; ++i) {
            FixtureUP fixture_up(new T(fixture));
            fixture_up->thread_id = i;
            fixture_up->num_threads = num_threads;
            threads.emplace_back(new TestThreadWrapper(_ignore, latch, barrier, traceStack, *fixture_up));
            fixtures.push_back(std::move(fixture_up));
        }
        for (size_t i = 1; i < num_threads; ++i) {
            pool.start([&target = *threads[i]](){ target.threadEntry(); });
        }
        threads[0]->threadEntry();
        latch.await();
        pool.join();
        bool result = true;
        for (size_t i = 0; i < num_threads; ++i) {
            result = result && threads[i]->getResult();
        }
        return result;
    }
    virtual bool run() = 0;

public:
    static void runAll();
};
#endif

} // namespace vespalib

