// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>

#include <algorithm>
#include <cstdlib>

#include <unistd.h>

#include <vespa/vespalib/util/size_literals.h>
#include "line-splitter.h"

namespace config {
namespace sentinel {


LineSplitter::LineSplitter(int fd)
    : _fd(fd),
      _size(8_Ki),
      _buffer(static_cast<char *>(malloc(_size))),
      _readPos(0),
      _writePos(0),
      _eof(false)
{
}

LineSplitter::~LineSplitter()
{
    free(_buffer);
}

bool
LineSplitter::resize()
{
    _size = _size * 2;
    _buffer = static_cast<char *>(realloc(_buffer, _size));
    return (_buffer != nullptr);
}


bool
LineSplitter::fill()
{
    // Check if we have read to end
    int leftToWrite = _writePos - _readPos;
    if (leftToWrite == 0) {
	_writePos = 0;
	_readPos = 0;
    } else if (_readPos > 0) { // Move to front
	memmove(_buffer, &_buffer[_readPos], leftToWrite);
	_writePos -= _readPos;
	_readPos = 0;
    }

    // If buffer is full, resize it
    if (_writePos >= _size) {
	if (!resize()) {
            _eof = true;
            shutdown(_fd, SHUT_RD);
            return false;
        }
    }

    int readLen = read(_fd, &_buffer[_writePos], _size - _writePos);
    if (readLen == -1) {
        if (errno != EINTR && errno != EAGAIN) {
            _eof = true;
        }
    } else if (readLen == 0) {
        _eof = true;
    } else {
        _writePos += readLen;
    }

    return readLen > 0;
}

char *
LineSplitter::getLine()
{
    do {
        int bufLen = _writePos - _readPos;

	if (bufLen > 0) {
            char *start = &_buffer[_readPos];
            char *end = static_cast<char *>(memchr(start, '\n', bufLen));
            if (_eof && !end) {
                if (_writePos < _size) {
                    end = &_buffer[_writePos]; // pretend last byte sent was followed by \n
                } else {
                    end = &_buffer[_writePos-1]; // pretend last byte sent was \n
                }
            }
            if (end) {
                *end = '\0';
                if (end - start > 0 && end[-1] == '\r') {
                    // Get rid of carriage return as well.
                    end[-1] = '\0';
                }
                _readPos = (end - _buffer) + 1;
                return start;
            }
	}
    } while (!_eof && fill());
    return nullptr;
}

} // end namespace config::sentinel
} // end namespace config


