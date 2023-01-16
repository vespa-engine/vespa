// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <cstdlib>

#include "log.h"
LOG_SETUP(".log");
#include "log-target-fd.h"
#include "internal.h"

namespace ns_log {

LogTargetFd::LogTargetFd(int fd_spec, const char *target)
    : LogTarget(target),
      _fd(-1), _istty(false)
{
    _fd = dup(fd_spec);
    if (_fd == -1) {
        throwInvalid("Bad target for LogTargetFd: '%s'", target);
    }
    if (isatty(_fd) == 1) {
        _istty = true;
    }
    fcntl(_fd, F_SETFD, FD_CLOEXEC);
}

LogTargetFd::~LogTargetFd()
{
    close(_fd);
}


// When writing to file descriptors, there is really not much to do.
// No log rotation is supported (at least not directly).
int
LogTargetFd::write(const char *buf, int bufLen)
{
    return ::write(_fd, buf, bufLen);
}

} // end namespace ns_log

