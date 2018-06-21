// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test_master.h"
#include <vespa/vespalib/util/barrier.h>
#include <cstring>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.testkit.test_master");

namespace vespalib {

namespace {

const char *skip_path(const char *file) {
    const char *last = strrchr(file, '/');
    return (last == nullptr) ? file : (last + 1);
}

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

TestMaster TestMaster::master;

//-----------------------------------------------------------------------------

__thread TestMaster::ThreadState *TestMaster::_threadState = 0;

//-----------------------------------------------------------------------------

TestMaster::TraceItem::~TraceItem() { }

TestMaster::ThreadState &
TestMaster::threadState(const vespalib::LockGuard &)
{
    if (_threadState == 0) {
        std::ostringstream threadName;
        threadName << "thread-" << _threadStorage.size();
        _threadStorage.push_back(std::make_unique<ThreadState>(threadName.str()));
        _threadState = _threadStorage.back().get();
    }
    return *_threadState;
}

TestMaster::ThreadState &
TestMaster::threadState()
{
    if (_threadState != 0) {
        return *_threadState;
    }
    {
        vespalib::LockGuard guard(_lock);
        return threadState(guard);
    }
}

//-----------------------------------------------------------------------------

void
TestMaster::checkFailed(const vespalib::LockGuard &guard,
                        const char *file, uint32_t line, const char *str)
{
    ThreadState &thread = threadState(guard);
    ++thread.failCnt;
    ++_state.failCnt;
    fprintf(stderr, "%s:%d: error: check failure #%zu: '%s' in thread '%s' (%s)\n",
            skip_path(file), line, _state.failCnt, str, thread.name.c_str(), _name.c_str());
    if (!thread.traceStack.empty()) {
        for (size_t i = thread.traceStack.size(); i-- > 0; ) {
            const TraceItem &item = thread.traceStack[i];
            fprintf(stderr, "    STATE[%zu]: '%s' (%s:%d)\n",
                    i, item.msg.c_str(), item.file.c_str(), item.line);
        }
    }
}

void
TestMaster::printDiff(const vespalib::LockGuard &guard,
                      const std::string &text, const std::string &file, uint32_t line,
                      const std::string &lhs, const std::string &rhs)
{
    ThreadState &thread = threadState(guard);
    if (_state.lhsFile == NULL || _state.rhsFile == NULL) {
        fprintf(stderr,
                "lhs: %s\n"
                "rhs: %s\n",
                lhs.c_str(), rhs.c_str());
    } else {
        fprintf(_state.lhsFile,
                "[check failure #%zu] '%s' in thread '%s' (%s:%d)\n"
                "%s\n",
                _state.failCnt, text.c_str(), thread.name.c_str(),
                file.c_str(), line, lhs.c_str());
        fprintf(_state.rhsFile,
                "[check failure #%zu] '%s' in thread '%s' (%s:%d)\n"
                "%s\n",
                _state.failCnt, text.c_str(), thread.name.c_str(),
                file.c_str(), line, rhs.c_str());
    }
}

void
TestMaster::handleFailure(const vespalib::LockGuard &guard, bool fatal)
{
    ThreadState &thread = threadState(guard);
    if (fatal) {
        if (thread.unwind) {
            throw Unwind();
        }
        fprintf(stderr, "%s: ERROR: vital check failed, aborting\n",
                _name.c_str());
        LOG_ABORT("should not be reached");
    }
}

void
TestMaster::closeDebugFiles(const vespalib::LockGuard &)
{
    if (_state.lhsFile != NULL) {
        fclose(_state.lhsFile);
        _state.lhsFile = NULL;
    }
    if (_state.rhsFile != NULL) {
        fclose(_state.rhsFile);
        _state.rhsFile = NULL;
    }
}

void
TestMaster::importThreads(const vespalib::LockGuard &)
{
    size_t importCnt = 0;
    for (size_t i = 0; i < _threadStorage.size(); ++i) {
        ThreadState &thread = *_threadStorage[i];
        _state.passCnt += thread.passCnt;
        importCnt += thread.passCnt;
        thread.passCnt = 0;
    }
    if (importCnt > 0) {
        fprintf(stderr, "%s: info:  imported %zu passed check(s) from %zu thread(s)\n",
                _name.c_str(), importCnt, _threadStorage.size());
    }
}

bool
TestMaster::reportConclusion(const vespalib::LockGuard &)
{
    bool ok = (_state.failCnt == 0);
    fprintf(stderr, "%s: info:  summary --- %zu check(s) passed --- %zu check(s) failed\n",
            _name.c_str(), _state.passCnt, _state.failCnt);
    fprintf(stderr, "%s: info:  CONCLUSION: %s\n", _name.c_str(), ok ? "PASS" : "FAIL");
    return ok;
}

//-----------------------------------------------------------------------------

TestMaster::TestMaster()
    : _lock(),
      _name("<unnamed>"),
      _path_prefix(get_source_dir() + "/"),
      _state(),
      _threadStorage()
{
    setThreadName("master");
}

//-----------------------------------------------------------------------------

void
TestMaster::init(const char *name)
{
    vespalib::LockGuard guard(_lock);
    _name = skip_path(name);
    fprintf(stderr, "%s: info:  running test suite '%s'\n", _name.c_str(), _name.c_str());
}

std::string
TestMaster::getName()
{
    vespalib::LockGuard guard(_lock);
    return _name;
}

std::string
TestMaster::get_path(const std::string &local_file)
{
    return (_path_prefix + local_file);
}

void
TestMaster::setThreadName(const char *name)
{
    threadState().name = name;
}

const char *
TestMaster::getThreadName()
{
    return threadState().name.c_str();
}

void
TestMaster::setThreadUnwind(bool unwind)
{
    threadState().unwind = unwind;
}

void
TestMaster::setThreadIgnore(bool ignore)
{
    ThreadState &thread = threadState();
    if (ignore == thread.ignore) {
        return;
    }
    if (ignore) {
        thread.ignore = true;
        thread.preIgnoreFailCnt = thread.failCnt;
    } else {
        thread.ignore = false;
        size_t revertCnt = (thread.failCnt - thread.preIgnoreFailCnt);
        thread.failCnt = thread.preIgnoreFailCnt;
        if (revertCnt > 0) {
            vespalib::LockGuard guard(_lock);
            assert(_state.failCnt >= revertCnt);
            _state.failCnt -= revertCnt;
        }
    }
}

void
TestMaster::setThreadBarrier(Barrier *barrier)
{
    threadState().barrier = barrier;
}

void
TestMaster::awaitThreadBarrier(const char *file, uint32_t line)
{
    ThreadState &thread = threadState();
    if (thread.barrier == 0) {
        return;
    }
    if (!thread.barrier->await()) {
        check(false, file, line, "test barrier broken", true);
    }
}

std::vector<TestMaster::TraceItem>
TestMaster::getThreadTraceStack()
{
    return threadState().traceStack;
}

void
TestMaster::setThreadTraceStack(const std::vector<TraceItem> &traceStack)
{
    threadState().traceStack = traceStack;
}

size_t
TestMaster::getThreadFailCnt()
{
    return threadState().failCnt;
}

TestMaster::Progress
TestMaster::getProgress()
{
    vespalib::LockGuard guard(_lock);
    return Progress(_state.passCnt, _state.failCnt);
}

void
TestMaster::openDebugFiles(const std::string &lhsFile,
                           const std::string &rhsFile)
{
    vespalib::LockGuard guard(_lock);
    closeDebugFiles(guard);
    _state.lhsFile = fopen(lhsFile.c_str(), "w");
    _state.rhsFile = fopen(rhsFile.c_str(), "w");
    if (_state.lhsFile == NULL || _state.rhsFile == NULL) {
        closeDebugFiles(guard);
        fprintf(stderr, "%s: Warn:  could not open debug files (%s, %s)\n",
                _name.c_str(), lhsFile.c_str(), rhsFile.c_str());
    } else {
        fprintf(_state.lhsFile, "[LHS]\n");
        fprintf(_state.rhsFile, "[RHS]\n");
    }
}

void
TestMaster::pushState(const char *file, uint32_t line, const char *msg)
{
    threadState().traceStack.push_back(TraceItem(skip_path(file), line, msg));
}

void
TestMaster::popState()
{
    ThreadState &state = threadState();
    if (!state.traceStack.empty()) {
        state.traceStack.pop_back();
    }
}

bool
TestMaster::check(bool rc, const char *file, uint32_t line,
                  const char *str, bool fatal)
{
    if (rc) {
        ++threadState().passCnt;
        return true;
    }
    {
        vespalib::LockGuard guard(_lock);
        checkFailed(guard, file, line, str);
        handleFailure(guard, fatal);
    }
    return false;
}

void
TestMaster::flush(const char *file, uint32_t line)
{
    ThreadState &thread = threadState();
    if (thread.passCnt > 0) {
        vespalib::LockGuard guard(_lock);
        _state.passCnt += thread.passCnt;
        fprintf(stderr, "%s: info:  flushed %zu passed check(s) from thread '%s' (%s:%d)\n",
                _name.c_str(), thread.passCnt, thread.name.c_str(), skip_path(file), line);
        thread.passCnt = 0;
    }
}

void
TestMaster::trace(const char *file, uint32_t line)
{
    ThreadState &thread = threadState();
    fprintf(stderr, "%s: info:  trace: thread '%s' (%s:%d)\n",
            _name.c_str(), thread.name.c_str(), skip_path(file), line);
}

bool
TestMaster::discardFailedChecks(size_t failCnt)
{
    vespalib::LockGuard guard(_lock);
    ThreadState &thread = threadState(guard);
    if (failCnt == _state.failCnt) {
        fprintf(stderr, "%s: info:  discarding %zu failed check(s)\n", _name.c_str(), _state.failCnt);
        _state.failCnt = 0;
        return true;
    } else {
        fprintf(stderr, "%s: ERROR: tried to discard %zu failed check(s), but was %zu (+1)\n",
                _name.c_str(), failCnt, _state.failCnt);
        ++thread.failCnt;
        ++_state.failCnt;
        return false;
    }
}

bool
TestMaster::fini()
{
    vespalib::LockGuard guard(_lock);
    closeDebugFiles(guard);
    importThreads(guard);
    return reportConclusion(guard);
}

//-----------------------------------------------------------------------------

} // namespace vespalib
