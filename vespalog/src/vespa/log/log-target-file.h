// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h>
#include <sys/stat.h>

#include <vespa/fastos/fastos.h>

#include "log-target.h"

namespace ns_log {

class LogTargetFile : public LogTarget {
private:
    char _fname[256];
    enum FailState {
        FS_OK, FS_CHECKING, FS_ROTATING, FS_FAILED
    } _failstate;

    LogTargetFile();
    LogTargetFile(const LogTargetFile&);
    LogTargetFile& operator =(const LogTargetFile&);

public:
    explicit LogTargetFile(const char *target);
    virtual ~LogTargetFile();
    virtual int write(const char *buf, int len);
};


} // end namespace log

