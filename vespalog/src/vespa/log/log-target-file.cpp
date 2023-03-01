// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "log.h"
LOG_SETUP(".log");
#include "log-target-file.h"
#include "internal.h"

#include <unistd.h>
#include <cstring>
#include <fcntl.h>
#include <cerrno>
#include <cassert>

namespace ns_log {

#ifndef O_LARGEFILE
#define O_LARGEFILE 0
#endif

LogTargetFile::LogTargetFile(const char *target)
    : LogTarget(target),
      _failstate(FS_OK)
{
    memset(_fname, 0, sizeof(_fname));
    const char *fname = target + strlen("file:");
    assert(strlen(fname) < sizeof(_fname));
    // NOTE: This function cannot LOG()
    if (strncmp(target, "file:", strlen("file:")) != 0) {
        throwInvalid("Illegal log target '%s'", target);
    }
    strcpy(_fname, fname);
    int fd = open(_fname, O_WRONLY | O_CREAT | O_APPEND | O_NOCTTY | O_LARGEFILE,
               0666);
    if (fd == -1) {
        throwInvalid("Cannot open log target file '%s': %s",
                     target + strlen("file:"), strerror(errno));
    }
    close(fd);
}

LogTargetFile::~LogTargetFile()
{
}

// Here we must support log rotation. We do this by reopening the filename.
int
LogTargetFile::write(const char *buf, int bufLen)
{
    int fd = open(_fname,
                  O_WRONLY | O_CREAT | O_APPEND | O_NOCTTY | O_LARGEFILE,
                  0666);
    if (fd < 0) {
        if (_failstate == FS_OK) {
            _failstate = FS_FAILED;
            LOG(warning, "cannot create %s: %s", _fname, strerror(errno));
            LOG(warning, "Log file failed, falling back to stderr logging");
        }
        fd = dup(STDERR_FILENO);
    } else {
        if (_failstate != FS_OK) {
            _failstate = FS_OK;
        }
    }
    int retVal = ::write(fd, buf, bufLen);
    close(fd);
    return retVal;
}

} // end namespace ns_log
