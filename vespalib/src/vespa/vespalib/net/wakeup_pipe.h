// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_handle.h"

namespace vespalib {

//-----------------------------------------------------------------------------

/**
 * A wakeup pipe is a non-blocking pipe that is used to wake up a
 * blocking call to epoll_wait. The pipe readability is part of the
 * selection set and a wakeup is triggered by writing to the
 * pipe. When a wakeup is detected, pending tokens will be read and
 * discarded to avoid spurious wakeups in the future.
 **/
class WakeupPipe {
private:
    SocketHandle _reader;
    SocketHandle _writer;
public:
    WakeupPipe();
    ~WakeupPipe();
    int get_read_fd() const { return _reader.get(); }
    void write_token();
    void read_tokens();
};

}
