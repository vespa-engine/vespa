// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>
#include <sys/types.h>

namespace search {

class StateFile;

/*
 * Class used to handle io error callsbacks from fastos.
 */
class IOErrorHandler
{
    static IOErrorHandler *_instance;
    StateFile *_stateFile;
    bool _trapped;
    bool _fired;

    using FailedHandler = void (*)(const char *op, const char *file, int error,
                                   int64_t offset, size_t len, ssize_t rlen);
    void trap();
    void untrap();

    static void forward(const char *op, const char *file, int error,
                        int64_t offset, size_t len, ssize_t rlen);

    void handle(const char *op, const char *file, int error,
                int64_t offset, size_t len, ssize_t rlen);

public:
    IOErrorHandler(StateFile *stateFile);
    ~IOErrorHandler();

    bool fired() const { return _fired; }
};


}
