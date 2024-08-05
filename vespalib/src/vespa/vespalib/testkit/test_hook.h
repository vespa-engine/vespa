// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "test_master.h"
#include <functional>

namespace vespalib {

class CountDownLatch;

struct TestThreadEntry {
    virtual void threadEntry() = 0;
    virtual ~TestThreadEntry() = default;
};

struct TestFixtureWrapper {
    size_t thread_id;
    size_t num_threads;
    TestFixtureWrapper() noexcept: thread_id(0), num_threads(1) {}
    virtual void test_entry_point() = 0;
    virtual ~TestFixtureWrapper() = default;
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
                      TestFixtureWrapper &fixture) noexcept
        : _result(false), _ignore(ignore),
          _latch(l), _barrier(b), _traceStack(traceStack),
          _fixture(fixture) {}

    void threadEntry() override;
    bool getResult() const noexcept {
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

    using FixtureFactory = std::function<std::unique_ptr<TestFixtureWrapper>()>;
    bool runMyTest(const FixtureFactory &fixture_factory, size_t num_threads);
protected:
    TestHook(const std::string &file, const std::string &name, bool ignore);
    virtual ~TestHook();

    template <typename T>
    bool runTest(const T &fixture, size_t num_threads) {
        return runMyTest([&fixture]() { return std::make_unique<T>(fixture); }, num_threads);
    }
    virtual bool run() = 0;

public:
    TestHook(const TestHook &) = delete;
    TestHook &operator=(const TestHook &) = delete;
    static void runAll();
};
#endif

} // namespace vespalib

