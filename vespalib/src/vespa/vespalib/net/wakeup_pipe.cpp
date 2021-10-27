// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wakeup_pipe.h"
#include "socket_utils.h"
#include <unistd.h>

namespace vespalib {

WakeupPipe::WakeupPipe()
    : _pipe()
{
    socketutils::nonblocking_pipe(_pipe);
}

WakeupPipe::~WakeupPipe()
{
    close(_pipe[0]);
    close(_pipe[1]);
}

void
WakeupPipe::write_token()
{
    char token = 'T';
    [[maybe_unused]] ssize_t res = write(_pipe[1], &token, 1);
}

void
WakeupPipe::read_tokens()
{
    char token_trash[128];
    [[maybe_unused]] ssize_t res = read(_pipe[0], token_trash, sizeof(token_trash));
}

}
