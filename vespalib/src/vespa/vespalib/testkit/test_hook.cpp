// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test_hook.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/size_literals.h>
#include <regex>
#include <vespa/fastos/thread.h>

namespace vespalib {

namespace {

struct FastOSTestThreadRunner : FastOS_Runnable {
    TestThreadEntry &entry;
    FastOSTestThreadRunner(TestThreadEntry &entry_in) : entry(entry_in) {}
    bool DeleteOnCompletion() const override { return true; }
    void Run(FastOS_ThreadInterface *, void *) override { entry.threadEntry(); }
};

struct FastOSTestThreadFactory : TestThreadFactory {
    FastOS_ThreadPool threadPool;
    FastOSTestThreadFactory() : threadPool(256_Ki) {}
    void createThread(TestThreadEntry &entry) override {
        threadPool.NewThread(new FastOSTestThreadRunner(entry), 0);
    }
};

} // namespace vespalib::<unnamed>

__thread TestThreadFactory *TestThreadFactory::factory = 0;

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
    master.setThreadBarrier(0);
    bool fail = (master.getThreadFailCnt() > preThreadFailCnt);
    master.setThreadUnwind(false);
    master.setThreadIgnore(false);
    _result = !fail;
    _latch.countDown();
    master.setThreadTraceStack(oldTraceStack);
    master.setThreadName(oldThreadName.c_str());
}

TestHook *TestHook::_head = 0;
TestHook *TestHook::_tail = 0;

TestHook::TestHook(const std::string &file, const std::string &name, bool ignore)
    : _next(0),
      _name(name),
      _tag(make_string("%s:%s", file.c_str(), name.c_str())),
      _ignore(ignore)
{
    if (_head == 0) {
        assert(_tail == 0);
        _head = this;
        _tail = this;
    } else {
        assert(_tail != 0);
        assert(_tail->_next == 0);
        _tail->_next = this;
        _tail = this;
    }
}

const char *lookup_subset_pattern(const std::string &name) {
    const char *pattern = getenv("TEST_SUBSET");
    if (pattern != 0) {
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
    FastOSTestThreadFactory threadFactory;
    TestThreadFactory::factory = &threadFactory;
    std::string name = TestMaster::master.getName();
    std::regex pattern(lookup_subset_pattern(name));
    size_t testsPassed = 0;
    size_t testsFailed = 0;
    size_t testsIgnored = 0;
    size_t testsSkipped = 0;
    for (TestHook *test = _head; test != 0; test = test->_next) {
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
    TestThreadFactory::factory = 0;
}

} // namespace vespalib
