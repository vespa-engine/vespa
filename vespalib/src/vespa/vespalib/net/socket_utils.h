// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::socketutils {

// Set blocking mode on file descriptor
void set_blocking(int fd,  bool blocking);

// Create a pipe and set it nonblocking
void nonblocking_pipe(int pipefd[2]);

// Create a socket pair and set it nonblocking
void nonblocking_socketpair(int domain, int type, int protocol, int socketfd[2]);

}
