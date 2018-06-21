// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statebuf.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.util.statebuf");


static const char *hexx = "0123456789abcdef";

namespace search {

void
StateBuf::overflow() noexcept
{
    LOG_ABORT("should not be reached");
}



StateBuf::StateBuf(void *buf, size_t bufLen) noexcept
    : _start(static_cast<char *>(buf)),
      _cur(static_cast<char *>(buf)),
      _end(static_cast<char *>(buf) + bufLen)
{
}


StateBuf &
StateBuf::operator<<(const char *s) noexcept
{
    for (const char *p = s; *p != '\0'; ++p) {
        *this << *p;
    }
    return *this;
}


StateBuf &
StateBuf::appendQuoted(const char *s) noexcept
{
    *this << '"';
    for (const char *p = s; *p != '\0'; ++p) {
        switch (*p) {
        case '\\':
            *this << '\\' << '\\';
            break;
        case '\n':
            *this << '\\' << 'n';
            break;
        case '"':
            *this << '\\' << '"';
            break;
        default:
            *this << *p;
        }
    }
    *this << '"';
    return *this;
}


StateBuf &
StateBuf::appendKey(const char *s) noexcept
{
    if (_cur != _start) {
        *this << ' ';
    }
    *this << s << '=';
    return *this;
}


StateBuf &
StateBuf::operator<<(unsigned long val) noexcept
{
    char buf[22];
    char *p = buf;
    for (; val != 0; ++p) {
        *p = '0' + (val % 10);
        val /= 10;
    }
    if (p == buf) {
        *this << '0';
    }
    while (p != buf) {
        --p;
        *this << *p;
    }
    return *this;
}


StateBuf &
StateBuf::operator<<(long val) noexcept
{
    if (val < 0) {
        *this << '-' << static_cast<unsigned long>(- val);
    } else {
        *this << static_cast<unsigned long>(val);
    }
    return *this;
}


StateBuf &
StateBuf::operator<<(unsigned int val) noexcept
{
    *this << static_cast<unsigned long>(val);
    return *this;
}


StateBuf &
StateBuf::operator<<(int val) noexcept
{
    *this << static_cast<long>(val);
    return *this;
}


StateBuf &
StateBuf::appendDecFraction(unsigned long val, unsigned int width) noexcept
{
    char buf[22];
    if (width > sizeof(buf)) {
        LOG_ABORT("should not be reached");
    }
    char *p = buf;
    char *pe = buf + width;
    for (; p != pe; ++p) {
        *p = '0' + (val % 10);
        val /= 10;
    }
    while (p != buf) {
        --p;
        *this << *p;
    }
    return *this;
}

StateBuf &
StateBuf::appendHex(unsigned long val) noexcept
{
    *this << "0x";
    for (int shft = 64; shft != 0;) {
        shft -= 4;
        *this << hexx[(val >> shft) & 15];
    }
    return *this;
}


StateBuf &
StateBuf::operator<<(const struct timespec &ts) noexcept
{
    (*this << static_cast<unsigned long>(ts.tv_sec) << '.').
        appendDecFraction(static_cast<unsigned long>(ts.tv_nsec), 9);
    return *this;
}


StateBuf &
StateBuf::appendTimestamp(const struct timespec &ts) noexcept
{
    appendKey("ts") << ts;
    return *this;
}


StateBuf &
StateBuf::appendTimestamp() noexcept
{
    struct timespec ts;
    /*
     * clock_gettime() is supposed to be async signal safe.
     * gettimeofday() is not documented to be async signal safe.
     */
    int gtres = clock_gettime(CLOCK_REALTIME, &ts);
    if (gtres != 0) {
        LOG_ABORT("should not be reached");
    }
    appendTimestamp(ts);
    return *this;
}


StateBuf &
StateBuf::appendAddr(void *addr) noexcept
{
    appendKey("addr");
    appendHex(reinterpret_cast<unsigned long>(addr));
    return *this;
}


size_t
StateBuf::size() const noexcept
{
    return _cur - _start;
};


const char *
StateBuf::base() const noexcept
{
    return _start;
}


std::string
StateBuf::str() const
{
    return std::string(_start, _cur);
}

}
