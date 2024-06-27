// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <functional>

namespace vespalib {

class Barrier;

#ifndef IAM_DOXYGEN
/**
 * The master of testing.
 **/
class TestMaster
{
public:
    struct Progress {
        size_t passCnt;
        size_t failCnt;
        Progress(size_t pass, size_t fail) : passCnt(pass), failCnt(fail) {}
    };

    struct Unwind {};

    static TestMaster master;

    struct TraceItem {
        std::string file;
        uint32_t    line;
        std::string msg;
        TraceItem(std::string file_in, uint32_t line_in, std::string msg_in);
        TraceItem(TraceItem &&) noexcept;
        TraceItem & operator=(TraceItem &&) noexcept;
        TraceItem(const TraceItem &);
        TraceItem & operator=(const TraceItem &);
        ~TraceItem();
    };

private:
    struct ThreadState {
        std::string            name;
        size_t                 passCnt;
        size_t                 failCnt;
        size_t                 preIgnoreFailCnt;
        bool                   ignore;
        bool                   unwind;
        std::vector<TraceItem> traceStack;
        Barrier               *barrier;
        ~ThreadState();
        ThreadState(std::string n);
        ThreadState(ThreadState &&) noexcept = default;
        ThreadState & operator=(ThreadState &&) noexcept = default;
    };
    static __thread ThreadState *_threadState;

    struct SharedState {
        size_t         passCnt;
        size_t         failCnt;
        FILE          *lhsFile;
        FILE          *rhsFile;
        SharedState() noexcept
            : passCnt(0), failCnt(0),
              lhsFile(nullptr), rhsFile(nullptr)
        {}
    };

    std::mutex                                 _lock;
    std::string                                _name;
    SharedState                                _state;
    std::vector<std::unique_ptr<ThreadState> > _threadStorage;
    using lock_guard = std::lock_guard<std::mutex>;

private:
    TestMaster();
    ThreadState &threadState(const lock_guard &);
    ThreadState &threadState();
    void checkFailed(const lock_guard &,
                     const char *file, uint32_t line, const char *str);
    void printDiff(const lock_guard &,
                   const std::string &text, const std::string &file, uint32_t line,
                   const std::string &lhs, const std::string &rhs);
    void handleFailure(const lock_guard &, bool do_abort);
    void closeDebugFiles(const lock_guard &);
    void importThreads(const lock_guard &);
    bool reportConclusion(const lock_guard &);


    void report_compare(const char *file, uint32_t line, const char *aName, const char *bName, const char *opText, bool fatal,
                        const std::function<void(std::ostream &)> & printLhs,
                        const std::function<void(std::ostream &)> & printRhs);
public:
    ~TestMaster();
    TestMaster(const TestMaster &) = delete;
    TestMaster &operator=(const TestMaster &) = delete;
    void init(const char *name);
    std::string getName();
    void setThreadName(const char *name);
    const char *getThreadName();
    void setThreadUnwind(bool unwind);
    void setThreadIgnore(bool ignore);
    void setThreadBarrier(Barrier *barrier);
    void awaitThreadBarrier(const char *file, uint32_t line);
    std::vector<TraceItem> getThreadTraceStack();
    void setThreadTraceStack(const std::vector<TraceItem> &traceStack);
    size_t getThreadFailCnt();
    Progress getProgress();
    void openDebugFiles(const std::string &lhsFile, const std::string &rhsFile);
    void close_debug_files();
    void pushState(const char *file, uint32_t line, const char *msg);
    void popState();
    bool check(bool rc, const char *file, uint32_t line, const char *str, bool fatal);
    template<class A, class B, class OP>
    bool compare(const char *file, uint32_t line,
                 const char *aName, const char *bName, const char *opText,
                 const A &a, const B &b, const OP &op, bool fatal);
    void flush(const char *file, uint32_t line);
    void trace(const char *file, uint32_t line);
    bool discardFailedChecks(size_t failCnt);
    bool fini();
};
#endif

} // namespace vespalib

#include "test_master.hpp"

