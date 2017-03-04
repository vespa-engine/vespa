// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sync.h>
#include <string>
#include <vector>
#include <memory>

namespace vespalib {

class Barrier;

#ifndef IAM_DOXYGEN
/**
 * The master of testing.
 **/
class TestMaster
{
private:
    TestMaster(const TestMaster &);
    TestMaster &operator=(const TestMaster &);

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
        TraceItem(const std::string &file_in, uint32_t line_in,
                  const std::string &msg_in)
            : file(file_in), line(line_in), msg(msg_in) {}
        ~TraceItem();
    };

private:
    struct ThreadState {
        std::string            name;
        bool                   unwind;
        size_t                 passCnt;
        size_t                 failCnt;
        bool                   ignore;
        size_t                 preIgnoreFailCnt;
        std::vector<TraceItem> traceStack;
        Barrier               *barrier;
        ThreadState(const std::string &n)
            : name(n), unwind(false), passCnt(0),
              failCnt(0), ignore(false), preIgnoreFailCnt(0), traceStack(),
              barrier(0) {}
    };
    static __thread ThreadState *_threadState;

    struct SharedState {
        size_t         passCnt;
        size_t         failCnt;
        FILE          *lhsFile;
        FILE          *rhsFile;
        SharedState() : passCnt(0), failCnt(0),
                        lhsFile(0), rhsFile(0) {}
    };

private:
    vespalib::Lock                                 _lock;
    std::string                                    _name;
    std::string                                    _path_prefix;
    SharedState                                    _state;
    std::vector<std::unique_ptr<ThreadState> > _threadStorage;

private:
    ThreadState &threadState(const vespalib::LockGuard &);
    ThreadState &threadState();
    void checkFailed(const vespalib::LockGuard &,
                     const char *file, uint32_t line, const char *str);
    void printDiff(const vespalib::LockGuard &,
                   const std::string &text, const std::string &file, uint32_t line,
                   const std::string &lhs, const std::string &rhs);
    void handleFailure(const vespalib::LockGuard &, bool do_abort);
    void closeDebugFiles(const vespalib::LockGuard &);
    void importThreads(const vespalib::LockGuard &);
    bool reportConclusion(const vespalib::LockGuard &);

private:
    TestMaster();

public:
    void init(const char *name);
    std::string getName();
    std::string get_path(const std::string &local_file);
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
    void pushState(const char *file, uint32_t line, const char *msg);
    void popState();
    bool check(bool rc, const char *file, uint32_t line,
               const char *str, bool fatal);
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

