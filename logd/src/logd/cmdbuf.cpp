// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/time.h>

#include <vespa/log/log.h>
LOG_SETUP("");
LOG_RCSID("$Id$");

#include "errhandle.h"
#include "service.h"
#include "forward.h"
#include "perform.h"
#include "cmdbuf.h"

namespace logdemon {

CmdBuf::CmdBuf()
    : _size(1000),
      _buf((char *)malloc(_size)),
      _bp(_buf),
      _left(_size)
{ }


CmdBuf::~CmdBuf()
{
    free(_buf);
}


bool
CmdBuf::hasCmd()
{
    char *p = _buf;
    while (p < _bp) {
        if (*p == '\n') return true;
        p++;
    }
    return false;

}

void
CmdBuf::doCmd(Performer& via)
{
    char *p = _buf;
    while (p < _bp) {
        if (*p == '\n') {
            *p = '\0';
            LOG(spam, "doing command: '%s'", _buf);
            via.doCmd(_buf);

            ++p;
            int len = p - _buf;
            int movelen = _bp - p;
            memmove(_buf, p, movelen);
            _bp -= len;
            _left += len;

            p = _buf;
            continue;
        }
        p++;
    }
}


void
CmdBuf::extend()
{
    _size *= 2;
    int pos = _bp - _buf;
    char *nbuf = (char *)realloc(_buf, _size);
    if (nbuf == NULL) {
        free(_buf);
        LOG(error, "could not allocate %d bytes", _size);
        throw SomethingBad("realloc failed");
    }
    _buf = nbuf;
    _bp = _buf + pos;
    _left = _size - pos;
}

#ifndef O_NONBLOCK
#define O_NONBLOCK O_NDELAY
#endif

void
CmdBuf::maybeRead(int fd)
{
    struct timeval notime;
    notime.tv_sec = 0;
    notime.tv_usec = 0;

    fd_set fdset;
    FD_ZERO(&fdset);
    FD_SET(fd, &fdset);
        
    while (select(fd + 1, &fdset, NULL, NULL, &notime) > 0) {
        // usually loops just once
        int oflags = fcntl(fd, F_GETFL);
        int nbflags = oflags | O_NONBLOCK;

        if (fcntl(fd, F_SETFL, nbflags) != 0) {
            LOG(error, "could not fcntl logserver socket: %s",
                strerror(errno));
            throw SomethingBad("fcntl failed");
        }

        ssize_t len = ::read(fd, _bp, _left);
        if (len > 0) {
            _bp += len;
            _left -= len;
            if (_left < 80) {
                extend();
            }
        } else if (len < 0) {
            LOG(warning, "error reading from logserver socket: %s",
                strerror(errno));
            throw ConnectionException("error reading");
        }
        fcntl(fd, F_SETFL, oflags);
        if (len == 0) {
            LOG(warning, "read 0 bytes from logserver socket");
            throw ConnectionException("eof on socket");
            break;
        }
    }
    return;
}


bool
CmdBuf::readFile(int fd)
{
    ssize_t len = ::read(fd, _bp, _left);
    if (len > 0) {
        _bp += len;
        _left -= len;
        if (_left < 80) {
            extend();
        }
        return true;
    }
    if (len < 0) {
        LOG(error, "error reading file: %s",
            strerror(errno));
        throw SomethingBad("read failed");
    }
    return false;
}

} // namespace
