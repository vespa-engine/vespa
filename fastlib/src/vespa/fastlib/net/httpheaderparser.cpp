// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "httpheaderparser.h"
#include <vespa/fastlib/io/bufferedinputstream.h>
#include <cstdio>
#include <cstring>

Fast_HTTPHeaderParser::Fast_HTTPHeaderParser(Fast_BufferedInputStream &in)
    : _pushBack(0),
      _isPushBacked(false),
      _bufferSize(16384),
      _lineBuffer(new char[_bufferSize]),
      _input(&in)
{
}

Fast_HTTPHeaderParser::~Fast_HTTPHeaderParser(void)
{
    delete [] _lineBuffer;
}

bool
Fast_HTTPHeaderParser::ReadRequestLine(const char *&method, const char *&url, int &versionMajor, int &versionMinor)
{
    // Read a single line from input.  Repeat if line is blank, to cope
    // with buggy HTTP/1.1 clients that print extra empty lines at the
    // end of requests.

    do {
        size_t idx = 0;
        ssize_t readLen = _input->ReadBufferFullUntil(_lineBuffer, _bufferSize, '\n');
        if (readLen <= 0) {
            return false;
        }
        idx = readLen-1;

        if (idx == 0 || _lineBuffer[idx] != '\n') {
            return false;
        }
        _lineBuffer[idx--] = '\0';
        if (_lineBuffer[idx] == '\r') {
            _lineBuffer[idx] = '\0';
        }
    } while (_lineBuffer[0] == '\0');

    // Parse request line.

    char *p = _lineBuffer;
    const char *version = "";

    method = p;
    p = strchr(p, ' ');
    if (p != NULL) {
        *p++ = '\0';
        url = p;
        p = strchr(p, ' ');
        if (p != NULL) {
            *p++ = '\0';
            version = p;
        }
    }

    if (sscanf(version, "HTTP/%d.%d", &versionMajor, &versionMinor) != 2) {
        versionMajor = versionMinor = -1;
        return false;
    }

    return true;
}

bool
Fast_HTTPHeaderParser::ReadHeader(const char *&name, const char *&value)
{
    size_t idx = 0;

    name  = NULL;
    value = NULL;

    if (_isPushBacked) {
        idx = 0;
        _lineBuffer[idx] = _pushBack;
        _isPushBacked = false;
        idx++;
    }

    constexpr size_t ROOM_FOR_PUSH_BACK = 1u;
    while ((idx + ROOM_FOR_PUSH_BACK) < _bufferSize) {
        ssize_t readLen = _input->ReadBufferFullUntil(&_lineBuffer[idx], _bufferSize - idx - ROOM_FOR_PUSH_BACK, '\n');
        if (readLen <= 0) {
            return false;
        }
        idx += readLen - 1;
        // Empty line == end of headers.
        // handle case with \r\n as \n
        if (idx == 0 || (_lineBuffer[0] == '\r' && idx == 1)) {
            idx = 0;
            break;
        }

        // Ignore double return 0xD, 0xA)
        if (idx >= 1) {
            if (_lineBuffer[idx - 1] == '\r') {
                idx--;
                _lineBuffer[idx] = '\n';
            }
        }

        // Check if header continues on next line.
        if (_input->Read(&_pushBack, 1) != 1) {
            break;
        }
        if (_pushBack == ' ' || _pushBack == '\t') {
            // Header does continue on next line.
            // Replace newline with horizontal whitespace.
            _lineBuffer[idx] = _pushBack;
            idx++;
        } else {
            _isPushBacked = true;
            // break out of while loop
            break;
        }
    }

    if (idx != 0) {
        _lineBuffer[idx] = '\0';
        char *p = _lineBuffer;
        name = p;

        // Find end of header name.
        while (*p != ':' && *p != '\0') {
            p++;
        }

        // If end of header name is not end of header, parse header value.
        if (*p != '\0') {
            // Terminate header name.
            *p++ = '\0';

            // Skip leading whitespace before header value.
            while (*p == ' ' || *p == '\t') {
                p++;
            }
            value = p;
            // Strip trailing whitespace.
            p += strlen(p);
            while (p > value && (*(p-1) == ' ' || *(p-1) == '\t')) {
                p--;
            }
            *p = '\0';
        } // End of header parsing (idx != 0).

    }

    return (idx != 0);
}
