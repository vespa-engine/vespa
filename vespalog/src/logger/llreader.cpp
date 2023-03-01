// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "llreader.h"
#include <cstdlib>
#include <cstring>
#include <unistd.h>

namespace ns_log {

InputBuf::InputBuf(int fd)
    : _inputfd(fd),
      _size(1000),
      _buf((char *)malloc(_size)),
      _bp(_buf),
      _left(_size)
{ }


InputBuf::~InputBuf()
{
    free(_buf);
}


bool
InputBuf::hasInput()
{
    char *p = _buf;
    while (p < _bp) {
        if (*p == '\n') return true;
        p++;
    }
    return false;

}

void
InputBuf::doInput(LLParser& via)
{
    char *p = _buf;
    while (p < _bp) {
        if (*p == '\n') {
            *p = '\0';
            via.doInput(_buf);
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
InputBuf::extend()
{
    _size *= 2;
    int pos = _bp - _buf;
    char *nbuf = (char *)realloc(_buf, _size);
    if (nbuf == NULL) {
        free(_buf);
        throw MsgException("realloc failed");
    }
    _buf = nbuf;
    _bp = _buf + pos;
    _left = _size - pos;
}

bool
InputBuf::blockRead()
{
    if (_left < 80) {
        extend();
    }
    ssize_t len = ::read(_inputfd, _bp, _left);
    if (len > 0) {
        _bp += len;
        _left -= len;
	// printf("read %d bytes: '%.*s'\n", len, len, _buf);
    } else if (len < 0) {
        throw MsgException("error reading");
    } else {
	// EOF on input
        return false;
    }
    return true;
}

void
InputBuf::doAllInput(LLParser &outputvia)
{
    while (blockRead()) {
        while (hasInput()) {
            doInput(outputvia);
        }
    }
    if (_bp != _buf) {
	if (_left < 1) extend();
	*_bp++ = '\n';
	doInput(outputvia);
    }
}

} // namespace
