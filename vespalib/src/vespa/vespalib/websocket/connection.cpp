// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "connection.h"
#include <vespa/vespalib/util/size_literals.h>
#include <cstdarg>
#include <cassert>

namespace vespalib::ws {

namespace {

void stripCR(vespalib::string &dst) {
    if (!dst.empty() && dst[dst.size() - 1] == '\r') {
        dst.resize(dst.size() - 1);
    }
}

Frame::Type type_from_opcode(char opcode) {
    switch (opcode) {
    case 0x0: return Frame::Type::NONE;
    case 0x1: return Frame::Type::TEXT;
    case 0x2: return Frame::Type::DATA;
    case 0x8: return Frame::Type::PING;
    case 0x9: return Frame::Type::PONG;
    case 0xa: return Frame::Type::CLOSE;
    default:  return Frame::Type::INVALID;
    }
}

char opcode_from_type(Frame::Type type) {
    switch (type) {
    case Frame::Type::NONE:  return 0x0;
    case Frame::Type::TEXT:  return 0x1;
    case Frame::Type::DATA:  return 0x2;
    case Frame::Type::PING:  return 0x8;
    case Frame::Type::PONG:  return 0x9;
    case Frame::Type::CLOSE: return 0xa;
    default:                 return 0xf;
    }
}

} // namespace vespalib::ws::<unnamed>


Connection::Connection(Socket::UP socket)
    : _socket(std::move(socket)),
      _input(),
      _output()
{
}

bool
Connection::fill_input(size_t min_bytes)
{
    while (_input.used() < min_bytes) {
        size_t max_read = (8_Ki);
        char *ptr = _input.reserve(max_read);
        ssize_t read_res = _socket->read(ptr, max_read);
        if (read_res > 0) {
            _input.commit(read_res);
        } else {
            return false;
        }
    }
    return true;
}

bool
Connection::read_line(string &dst)
{
    dst.clear();
    for (int c = read_byte(); c >= 0; c = read_byte()) {
        if (c != '\n') {
            dst.push_back(c);
        } else {
            stripCR(dst);
            return true;
        }
    }
    return !dst.empty();
}

bool
Connection::read_frame(Frame &frame)
{
    if (!fill_input(2)) {
        return false;
    }
    char h1 = _input.next();
    char h2 = _input.next();
    frame.type = type_from_opcode(h1 & 0x0f);
    frame.last = ((h1 & 0x80) != 0);
    frame.payload.clear();
    size_t len = (h2 & 0x7f);
    if (len > 125) {
        size_t len_bytes = (len == 127) ? 8 : 2;
        if (!fill_input(len_bytes)) {
            return false;
        }
        len = 0;
        for (size_t i = 0; i < len_bytes; ++i) {
            len = (len << 8) + (_input.next() & 0xff);
        }
    }
    char mask[4];
    bool use_mask = ((h2 & 0x80) != 0);
    if (use_mask) {
        if (!fill_input(4)) {
            return false;
        }
        for (size_t i = 0; i < 4; ++i) {
            mask[i] = _input.next();
        }
    }
    if (!fill_input(len)) {
        return false;
    }
    const char *src = _input.obtain();
    char *dst = frame.payload.reserve(len);
    if (use_mask) {
        for (size_t i = 0; i < len; ++i) {
            dst[i] = (src[i] ^ mask[i & 0x3]);
        }
    } else {
        memcpy(dst, src, len);
    }
    frame.payload.commit(len);
    _input.evict(len);
    return true;
}

void
Connection::write_frame(const Frame &frame)
{
    size_t len = frame.payload.used();
    bool large_len = (len > 125);
    bool huge_len = (len > 0xffFF);
    char h1 = opcode_from_type(frame.type);
    if (frame.last) {
        h1 |= 0x80;
    }
    char h2 = (large_len) ? 126 : len;
    if (huge_len) {
        ++h2;
    }
    _output.push(h1);
    _output.push(h2);
    if (large_len) {
        size_t len_bytes = (huge_len) ? 8 : 2;
        size_t len_src = len;
        char *len_dst = _output.reserve(len_bytes);
        for (size_t i = len_bytes; i > 0; --i) {
            len_dst[i - 1] = (len_src & 0xff);
            len_src >>= 8;
        }
        _output.commit(len_bytes);
    }
    char *dst = _output.reserve(len);
    memcpy(dst, frame.payload.obtain(), len);
    _output.commit(len);
}

void
Connection::printf(const char *fmt, ...)
{
    char *dst = _output.reserve(256);
    int space = _output.free();
    int size;
    va_list ap;
    va_start(ap, fmt);
    size = vsnprintf(dst, space, fmt, ap);
    va_end(ap);
    assert(size >= 0);
    if (size >= space) {
        space = size + 1;
        dst = _output.reserve(space);
        va_start(ap, fmt);
        size = vsnprintf(dst, space, fmt, ap);
        va_end(ap);
        assert((size + 1) == space);
    }
    _output.commit(size);
}

void
Connection::write(const char *data, size_t len)
{
    char *dst = _output.reserve(len);
    memcpy(dst, data, len);
    _output.commit(len);
}

bool
Connection::flush()
{
    while (_output.used() > 0) {
        ssize_t write_res = _socket->write(_output.obtain(), _output.used());
        if (write_res > 0) {
            _output.evict(write_res);
        } else {
            return false;
        }
    }
    return true;
}

} // namespace vespalib::ws
