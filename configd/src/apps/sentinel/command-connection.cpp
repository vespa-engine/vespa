// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <unistd.h>
#include <stdarg.h>
#include <cstdio>
#include <cstring>
#include <sys/socket.h>

#include "command-connection.h"
#include "line-splitter.h"

namespace config {
namespace sentinel {

CommandConnection::CommandConnection(int f)
    : _fd(f),
      _lines(f)
{
}

bool
CommandConnection::isFinished() const
{
    return _lines.eof();
}

char *
CommandConnection::getCommand()
{
    return _lines.getLine();
}

CommandConnection::~CommandConnection()
{
    close(_fd);
}

void
CommandConnection::finish()
{
    ::shutdown(_fd, SHUT_RDWR);
}

int
CommandConnection::printf(const char *fmt, ...)
{
    char buf[10000];
    va_list args;
    va_start(args, fmt);

    int ret = vsnprintf(buf, sizeof buf, fmt, args);
    va_end(args);

    ssize_t len = strlen(buf);
    if (write(_fd, buf, len) != len) {
	perror("CommandConnection::printf failed");
    }
    return ret;
}

} // end namespace config::sentinel
} // end namespace config
