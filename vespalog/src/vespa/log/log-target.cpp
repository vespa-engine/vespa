// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cstring>
#include <cassert>

#include "log.h"
LOG_SETUP(".log");

#include "log-target-fd.h"
#include "log-target-file.h"
#include "internal.h"

namespace ns_log {

LogTarget::LogTarget(const char *target)
{
  memset(_name, 0, sizeof(_name));
  assert(strlen(target) < sizeof(_name));
  strcpy(_name, target);
}

LogTarget::~LogTarget() {
}

LogTarget *
LogTarget::defaultTarget()
{
    // Note! This function cannot LOG().
    return new LogTargetFd(2, "fd:2");
}

LogTarget *
LogTarget::makeTarget(const char *const target)
{
    if (strncmp(target, "fd:", 3) == 0) {
        int fd_spec = strtol(target + 3, NULL, 0);
        if (fd_spec > 0) {
            return new LogTargetFd(fd_spec, target);
        }
    } else if (strncmp(target, "file:", 5) == 0) {
        return new LogTargetFile(target);
    }
    throwInvalid("Log target '%s' is invalid.", target);
}


} // end namespace ns_log
