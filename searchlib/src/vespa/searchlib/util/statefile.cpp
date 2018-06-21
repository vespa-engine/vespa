// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statefile.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <system_error>
#include <mutex>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.util.statefile");

using Mutex = std::mutex;
using Guard = std::lock_guard<Mutex>;

namespace search {

namespace {

Mutex stateMutex;

/*
 * Assumes that std::atomic implementation is lock free, which it is
 * for gcc 4.9.2.  Usage is not async signal safe unless the
 * implementation is lock free.
 */
std::atomic<int> nestingCount;

int
myopen(const char *name) noexcept
{
    int fd = open(name, O_CREAT | O_CLOEXEC | O_SYNC | O_RDWR, 0644);
    if (fd < 0) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr,
                "Could not open %s: %s\n", name, ec.message().c_str());
        LOG_ABORT("should not be reached");
    }
    return fd;
}


void
myfstat(const char *name, int fd, struct stat &stbuf) noexcept
{
    int fsres = fstat(fd, &stbuf);
    if (fsres != 0) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Could not fstat %s: %s\n", name, ec.message().c_str());
        LOG_ABORT("should not be reached");
    }
}


void
mypread(const char *name, int fd, void *buf, size_t bufLen, int64_t offset) noexcept
{
    ssize_t rres = pread(fd, buf, bufLen, offset);
    if (static_cast<size_t>(rres) != bufLen) {
        if (rres >= 0) {
            fprintf(stderr,
                    "Could not read %zu bytes from %s offset %" PRId64
                ": short read (%zd)\n",
                bufLen, name, offset, rres);
        } else {
            std::error_code ec(errno, std::system_category());
            fprintf(stderr,
                    "Could not read %zu bytes from %s offset %" PRId64 ": %s\n",
                    bufLen, name, offset, ec.message().c_str());
        }
        LOG_ABORT("should not be reached");
    }
}


void
mypwrite(const char *name, int fd, const void *buf, size_t bufLen,
         int64_t offset) noexcept
{
    ssize_t wres = pwrite(fd, buf, bufLen, offset);
    if (static_cast<size_t>(wres) != bufLen) {
        if (wres >= 0) {
            fprintf(stderr,"Could not write %zu bytes to %s offset %" PRId64
                    ": short write (%zd)\n",
                bufLen, name, offset, wres);
        } else {
            std::error_code ec(errno, std::system_category());
            fprintf(stderr,
                    "Could not write %zu bytes to %s offset %" PRId64 ": %s\n",
                    bufLen, name, offset, ec.message().c_str());
        }
        LOG_ABORT("should not be reached");
    }
}


void
myclose(const char *name, int fd) noexcept
{
    int closeres = close(fd);
    if (closeres != 0) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Could not close %s: %s\n",
                name, ec.message().c_str());
        LOG_ABORT("should not be reached");
    }
}


void
myfsync(const char *name, int fd) noexcept
{
    int fsyncres = fsync(fd);
    if (fsyncres != 0) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Could not fsync %s: %s\n",
            name, ec.message().c_str());
        LOG_ABORT("should not be reached");
    }
}


void
myunlink(const char *name) noexcept
{
    int unlinkres = unlink(name);
    if (unlinkres != 0 && errno != ENOENT) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Could not unlink %s: %s\n",
                name, ec.message().c_str());
        LOG_ABORT("should not be reached");
    }
}


/*
 * Write string to standard error using only async signal safe methods.
 */
void
mystderr(const char *msg) noexcept
{
    const char *p = msg;
    while (*p != '\0') {
        ++p;
    }
    write(STDERR_FILENO, msg, static_cast<size_t>(p - msg));
}


/*
 * Get async signal safe spinlock.
 */
void
getLock() noexcept
{
    int expzero = 0;
    while (!nestingCount.compare_exchange_weak(expzero, 1)) {
        expzero = 0;
        sleep(1);
    }
}


/*
 * Release async signal safe spinlock
 */
void
releaseLock() noexcept
{
    nestingCount = 0;
}

class SpinGuard
{
public:
    SpinGuard() noexcept
    {
        getLock();
    }

    ~SpinGuard() noexcept
    {
        releaseLock();
    }
};

}


StateFile::StateFile(const std::string &name)
    : _name(nullptr),
      _historyName(nullptr),
      _gen(0)
{
    _name = strdup(name.c_str());
    std::string historyName = name + ".history";
    _historyName = strdup(historyName.c_str());
    zeroPad();
    fixupHistory();
}


StateFile::~StateFile()
{
    free(_name);
    free(_historyName);
}


void
StateFile::erase(const std::string &name)
{
    std::string historyName = name + ".history";
    myunlink(name.c_str());
    myunlink(historyName.c_str());
}


void
StateFile::readRawState(std::vector<char> &buf)
{
    struct stat stbuf;
    Guard guard(stateMutex); // Serialize states
    SpinGuard spinGuard;
    int fd = myopen(_name);
    myfstat(_name, fd, stbuf);
    buf.resize(stbuf.st_size);
    mypread(_name, fd, &buf[0], buf.size(), 0);
    myclose(_name, fd);
}


void
StateFile::trimState(std::vector<char> &buf)
{
    auto newBufEnd = buf.cbegin();
    auto bufEnd = buf.cend();
    for (auto p = buf.cbegin(); p != bufEnd; ++p) {
        if (*p == '\n') { // End of state string
            newBufEnd = p + 1;
            break;        // stop scanning after first state
        }
        if (*p == '\0') { // padding encountered, stop scanning for end
            break;
        }
    }
    size_t newStateSize = newBufEnd - buf.cbegin();
    buf.resize(newStateSize);
}


void
StateFile::readState(std::vector<char> &buf)
{
    readRawState(buf);
    trimState(buf);
}


void
StateFile::trimHistory(std::vector<char> &history, const char *name, int hfd,
                       std::vector<char> &lastHistoryState)
{
    auto historyEnd = history.cend();
    auto prevHistoryEnd = history.cbegin();
    auto newHistoryEnd = history.cbegin();
    for (auto p = history.cbegin(); p != historyEnd; ++p) {
        if (*p == '\n') { // End of state string
            prevHistoryEnd = newHistoryEnd;
            newHistoryEnd = p + 1;
        }
        if (*p == '\0') { // corruption, stop scanning for end
            break;
        }
    }
    std::vector<char> historyEntry(prevHistoryEnd, newHistoryEnd);
    size_t newHistSize = newHistoryEnd - history.cbegin();
    if (newHistSize != history.size()) {
        int ftruncres = ftruncate(hfd, newHistSize);
        if (ftruncres != 0) {
            std::error_code ec(errno, std::system_category());
            fprintf(stderr, "Could not truncate %s: %s\n",
                    name, ec.message().c_str());
            LOG_ABORT("should not be reached");
        }
        history.resize(newHistSize);
    }
    historyEntry.swap(lastHistoryState);
}

/*
 * Fixup history after failed append, e.g. trucated write caused partial
 * last state.
 */
void
StateFile::fixupHistory()
{
    struct stat sthbuf;
    int hfd = myopen(_historyName);
    myfstat(_historyName, hfd, sthbuf);
    std::vector<char> history(sthbuf.st_size);
    mypread(_historyName, hfd, &history[0], history.size(), 0);
    std::vector<char> lastHistory;
    trimHistory(history, _historyName, hfd, lastHistory);
    std::vector<char> buf;
    readState(buf);
    if (!buf.empty() && buf != lastHistory) {
        mypwrite(_historyName, hfd, &buf[0], buf.size(), history.size());
        myfsync(_historyName, hfd);
    }
    myclose(_historyName, hfd);
    if (buf.empty() && !lastHistory.empty()) {
        // Restore state in main state file from last state in history.
        int fd = myopen(_name);
        mypwrite(_name, fd, &lastHistory[0], lastHistory.size(), 0);
        myfsync(_name, fd);
        myclose(_name, fd);
    }
}


void
StateFile::zeroPad()
{
    struct stat stbuf;
    int minSize = 4096;
    int fd = myopen(_name);
    myfstat(_name, fd, stbuf);
    std::vector<char> buf(minSize);
    if (stbuf.st_size < minSize) {
        int padSize = minSize - stbuf.st_size;
        mypwrite(_name, fd, &buf[0], padSize, stbuf.st_size);
        myfsync(_name, fd);
    }
    myclose(_name, fd);
}


void
StateFile::checkState(const char *buf, size_t bufLen) noexcept
{
    const char *pe = buf + bufLen;
    for (const char *p = buf; p < pe; ++p) {
        if (*p == '\n') {
            if (p != buf + bufLen - 1) {
                mystderr("statefile state corrupted: early newline\n");
                LOG_ABORT("should not be reached");
            }
            return;
        }
        if (*p == '\0') {
            mystderr("statefile state corrupted: nul byte found\n");
            LOG_ABORT("should not be reached");
        }
    }
    mystderr("statefile state corrupted: missing newline at end\n");
    LOG_ABORT("should not be reached");
}


void
StateFile::internalAddSignalState(const char *buf, size_t bufLen,
                                  const char *name,
                                  int appendFlag,
                                  const char *openerr,
                                  const char *writeerr,
                                  const char *fsyncerr,
                                  const char *closeerr) noexcept
{
        // Write to main state file, overwriting previous state
    int fd = open(name, O_CREAT | O_CLOEXEC | O_SYNC | O_RDWR | appendFlag,
                  0644);
    if (fd < 0) {
        mystderr(openerr);
        LOG_ABORT("should not be reached");
    }
    ssize_t wres = write(fd, buf, bufLen);
        if (static_cast<size_t>(wres) != bufLen) {
            mystderr(writeerr);
            LOG_ABORT("should not be reached");
        }
        int fsyncres = fsync(fd);
        if (fsyncres != 0) {
            mystderr(fsyncerr);
            LOG_ABORT("should not be reached");
        }
        int closeres = close(fd);
        if (closeres != 0) {
            mystderr(closeerr);
            LOG_ABORT("should not be reached");
        }
}

/*
 * Write state string to file.  State string contains one newline, at the end.
 *
 * Async signal safe functions used:
 * open(), write(), fsync(), close()
 *
 * Is in signal handler, thus cannot throw exception.
 */
void
StateFile::addSignalState(const char *buf, size_t bufLen) noexcept
{
    checkState(buf, bufLen);
    SpinGuard spinGuard;
    // Write to main state file, overwriting previous state
    internalAddSignalState(buf, bufLen, _name, 0,
                           "Could not open statefile for read/write\n",
                           "Error writing to statefile\n",
                           "Error syncing statefile\n",
                           "Error closing statefile\n");
    // Write to state file history, appending
    internalAddSignalState(buf, bufLen, _historyName, O_APPEND,
                           "Could not open statefile history for read/write\n",
                           "Error writing to statefile history\n",
                           "Error syncing statefile history\n",
                           "Error closing statefile history\n");
    ++_gen;
}

/*
 * Write state string to file.  State string contains one newline, at the end.
 */
void
StateFile::addState(const char *buf, size_t bufLen, bool signal)
{
    if (signal) {
        // In signal context, degraded error reporting on state file failures
        addSignalState(buf, bufLen);
        return;
    }
    checkState(buf, bufLen);
    Guard guard(stateMutex); // Serialize states
    SpinGuard spinGuard;
    {
        // Write to main state file, overwriting previous state
        int fd = myopen(_name);
        mypwrite(_name, fd, buf, bufLen, 0);
        myfsync(_name, fd);
        myclose(_name, fd);
    }
    {
        // Write to state file history, appending
        int hfd = myopen(_historyName);
        struct stat sthbuf;
        myfstat(_historyName, hfd, sthbuf);
        mypwrite(_historyName, hfd, buf, bufLen, sthbuf.st_size);
        myfsync(_historyName, hfd);
        myclose(_historyName, hfd);
    }
    ++_gen;
}


int
StateFile::getGen() const
{
    return _gen;
}

}
