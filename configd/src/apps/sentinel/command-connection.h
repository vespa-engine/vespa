// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "line-splitter.h"

namespace config::sentinel {

class CommandConnection {
private:
    int _fd;
    LineSplitter _lines;

    // Unused constructors/assignment operator:
    CommandConnection();
    CommandConnection(const CommandConnection&);
    CommandConnection& operator =(const CommandConnection&);

public:
    explicit CommandConnection(int fd);
    ~CommandConnection();
    bool isFinished() const;
    char *getCommand();
    int printf(const char *fmt, ...) __attribute__((format(printf, 2, 3)));
    void finish();
    int fd() const { return _fd; }
};

}
