// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/file.h>
#include <cstring>
#include <unistd.h>
#include <limits.h>
#include <cstdio>

#include "log.h"
LOG_SETUP_INDIRECT(".log.lock", "$Id$");
#include "lock.h"
#include "internal.h"

#undef LOG
#define LOG LOG_INDIRECT

namespace ns_log {

Lock::Lock(int arg_fd)
    : _fd(dup(arg_fd)),
      _isLocked(false)
{
    if (_fd == -1) {
        throwInvalid("Cannot duplicate fd %d for lock: %s", arg_fd,
                     strerror(errno));
    }
}


Lock::Lock(const char *filename, int mode)
    : _fd(open(filename, mode, 0666)),
      _isLocked(false)
{
    if (_fd == -1) {
        if (mode & O_CREAT) {
            unlink(filename);
            _fd = open(filename, mode, 0666);
        }
        if (_fd == -1) {
            throwInvalid("Cannot open lockfile '%s': %s", filename,
                         strerror(errno));
        }
    }
    fcntl(_fd, F_SETFD, FD_CLOEXEC);
}

void
Lock::lock(bool exclusive)
{
    // Early escape to avoid double locking which would of course block forever
    if (_isLocked) return;

    if (flock(_fd, exclusive ? LOCK_EX : LOCK_SH) == -1) {
        int err = errno;
        close(_fd);
        LOG(error, "Cannot lock fd %d: %s", _fd, strerror(err));
        throwInvalid("Cannot lock fd %d: %s", _fd, strerror(err));
    }
    _isLocked = true;
}

void
Lock::unlock() {
    if (_isLocked) {
        flock(_fd, LOCK_UN);
        _isLocked = false;
    }
}

int Lock::size()
{
    struct stat st;
    int err = fstat(_fd, &st);
    if (err == -1) {
        return -1;
    }
    return st.st_size;
}

Lock::~Lock()
{
    unlock();
    close(_fd);
}

} // end namespace ns_log
