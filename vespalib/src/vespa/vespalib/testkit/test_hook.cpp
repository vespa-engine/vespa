// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test_hook.h"
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>
#include <regex>

namespace vespalib {

void
TestThreadWrapper::threadEntry()
{
    TestMaster &master = TestMaster::master;
    std::string oldThreadName = master.getThreadName();
    std::vector<TestMaster::TraceItem> oldTraceStack = master.getThreadTraceStack();
    master.setThreadName(make_string("%zu(%zu)", _fixture.thread_id, _fixture.num_threads).c_str());
    master.setThreadTraceStack(_traceStack);
    size_t preThreadFailCnt = master.getThreadFailCnt();
    master.setThreadIgnore(_ignore);
    master.setThreadUnwind(true);
    master.setThreadBarrier(&_barrier);
    _barrier.await();
    try {
        _fixture.test_entry_point();
    } catch (TestMaster::Unwind &) {
    } catch (std::exception &e) {
        master.check(false, __FILE__, __LINE__, e.what(), false);
    } catch (...) {
        master.check(false, __FILE__, __LINE__, "test threw stuff", false);
    }
    _barrier.destroy();
    master.setThreadBarrier(nullptr);
    bool fail = (master.getThreadFailCnt() > preThreadFailCnt);
    master.setThreadUnwind(false);
    master.setThreadIgnore(false);
    _result = !fail;
    _latch.countDown();
    master.setThreadTraceStack(oldTraceStack);
    master.setThreadName(oldThreadName.c_str());
}

TestHook *TestHook::_head = nullptr;
TestHook *TestHook::_tail = nullptr;

TestHook::~TestHook() = default;

TestHook::TestHook(const std::string &file, const std::string &name, bool ignore)
    : _next(nullptr),
      _name(name),
      _tag(make_string("%s:%s", file.c_str(), name.c_str())),
      _ignore(ignore)
{
    if (_head == nullptr) {
        assert(_tail == nullptr);
        _head = this;
        _tail = this;
    } else {
        assert(_tail != nullptr);
        assert(_tail->_next == nullptr);
        _tail->_next = this;
        _tail = this;
    }
}

bool TestHook::runMyTest(const FixtureFactory & fixture_factory, size_t num_threads) {
    assert(num_threads > 0);
    using ThreadUP = std::unique_ptr<TestThreadWrapper>;
    using FixtureUP = std::unique_ptr<TestFixtureWrapper>;
    std::vector<TestMaster::TraceItem> traceStack = TestMaster::master.getThreadTraceStack();
    CountDownLatch latch(num_threads);
    Barrier barrier(num_threads);
    std::vector<FixtureUP> fixtures;
    std::vector<ThreadUP> threads;
    ThreadPool pool;
    threads.reserve(num_threads);
    fixtures.reserve(num_threads);
    for (size_t i = 0; i < num_threads; ++i) {
        FixtureUP fixture_up = fixture_factory();
        fixture_up->thread_id = i;
        fixture_up->num_threads = num_threads;
        threads.emplace_back(std::make_unique<TestThreadWrapper>(_ignore, latch, barrier, traceStack, *fixture_up));
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

const char *lookup_subset_pattern(const std::string &name) {
    const char *pattern = getenv("TEST_SUBSET");
    if (pattern != nullptr) {
        fprintf(stderr, "%s: info:  only running tests matching '%s'\n",
                name.c_str(), pattern);
    } else {
        pattern = "";
    }
    return pattern;
}

void
TestHook::runAll()
{
    std::string name = TestMaster::master.getName();
    std::regex pattern(lookup_subset_pattern(name));
    size_t testsPassed = 0;
    size_t testsFailed = 0;
    size_t testsIgnored = 0;
    size_t testsSkipped = 0;
    for (TestHook *test = _head; test != nullptr; test = test->_next) {
        if (std::regex_search(test->_tag, pattern)) {
            bool ignored = test->_ignore;
            bool failed = !test->run();
            if (ignored) {
                ++testsIgnored;
            } else if (failed) {
                ++testsFailed;
            } else {
                ++testsPassed;
            }
            const char *level = ignored ? "Warn: " : failed ? "ERROR:" : "info: ";
            fprintf(stderr, "%s: %s status_for_test '%s': %s%s\n",
                    name.c_str(), level, test->_name.c_str(),
                    failed ? "FAIL" : "PASS",
                    ignored ? " (ignored)" : "");
        } else {
            ++testsSkipped;
        }
    }
    fprintf(stderr, "%s: info:  test summary --- %zu test(s) passed --- %zu test(s) failed\n",
            name.c_str(), testsPassed, testsFailed);
    if (testsSkipped > 0) {
        fprintf(stderr, "%s: info:  test summary --- %zu test(s) skipped\n",
                name.c_str(), testsSkipped);
    }
    if (testsIgnored > 0) {
        fprintf(stderr, "%s: Warn:  test summary --- %zu test(s) ignored\n",
                name.c_str(), testsIgnored);
    }
}

} // namespace vespalib
