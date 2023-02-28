// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wakeup_pipe.h"
#include "socket_utils.h"
#include <vespa/vespalib/util/require.h>
#include <unistd.h>

namespace vespalib {

WakeupPipe::WakeupPipe()
  : _reader(),
    _writer()
{
    int pipe[2];
    socketutils::nonblocking_pipe(pipe);
    _reader.reset(pipe[0]);
    _writer.reset(pipe[1]);
}

WakeupPipe::~WakeupPipe() = default;

void
WakeupPipe::write_token()
{
    char token = 'T';
    ssize_t res = _writer.write(&token, 1);
    if (res < 0) {
        res = -errno;
    }
    REQUIRE(res > 0 || res == -EAGAIN || res == -EWOULDBLOCK);
}

void
WakeupPipe::read_tokens()
{
    char token_trash[128];
    ssize_t res = _reader.read(token_trash, sizeof(token_trash));
    REQUIRE(res > 0);
}

}
