// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "log-target.h"

namespace ns_log {

class LogTargetFd : public LogTarget {
private:
    int _fd;
    bool _istty;
    LogTargetFd() = delete;
    LogTargetFd(const LogTargetFd&) = delete;
    LogTargetFd& operator= (const LogTargetFd) = delete;

public:
    LogTargetFd(int fd_spec, const char *target);
    int write(const char *buf, int len) override;
    ~LogTargetFd();
    bool makeHumanReadable() const override { return _istty; }
};


} // end namespace log

