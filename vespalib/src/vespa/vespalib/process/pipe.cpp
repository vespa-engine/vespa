// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pipe.h"
#include <unistd.h>

namespace vespalib {

Pipe
Pipe::create()
{
    int my_pipe[2];
    if (pipe(my_pipe) == 0) {
        return {FileDescriptor(my_pipe[0]),
                FileDescriptor(my_pipe[1])};
    }
    return {FileDescriptor(),FileDescriptor()};
}

Pipe::~Pipe() = default;

} // namespace vespalib
