// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "log-target.h"

namespace ns_log {

class LogTargetFd : public LogTarget {
private:
    int _fd;
    bool _istty;
    LogTargetFd();
    LogTargetFd(const LogTargetFd&);
    LogTargetFd& operator = (const LogTargetFd);

public:
    explicit LogTargetFd(const char *target);
    virtual int write(const char *buf, int len);
    virtual ~LogTargetFd();
    virtual bool makeHumanReadable() const { return _istty; }
};


} // end namespace log

