// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h>
#include <fcntl.h>
#include <limits.h>

namespace ns_log {

class Lock {
private:
    Lock();
    Lock(const Lock &);
    Lock& operator =(const Lock &);
    int _fd;
    bool _isLocked;


public:
    int fd() const { return _fd; }
    int size();
    explicit Lock(const char *filename, int mode = O_RDONLY | O_NOCTTY);
    explicit Lock(int fd);
    ~Lock();
    void lock(bool isExclusive);
    void unlock();
};


} // end namespace ns_log


