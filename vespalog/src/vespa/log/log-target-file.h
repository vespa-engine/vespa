// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "log-target.h"
#include <sys/types.h>
#include <sys/stat.h>

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
    ~LogTargetFile();
    int write(const char *buf, int len) override;
};


} // end namespace log

