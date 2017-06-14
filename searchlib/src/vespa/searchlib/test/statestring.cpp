// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statestring.h"
#include <sstream>

namespace search::test::statestring {

bool
testStartPos(vespalib::string &s, size_t pos)
{
    return (pos < s.size() && (pos == 0 || s[pos - 1] == ' '));
}

size_t
findStartPos(vespalib::string &s, const vespalib::string &key)
{
    size_t pos = 0;
    while (pos < s.size()) {
        pos = s.find(key, pos);
        if (testStartPos(s, pos)) {
            break;
        }
        ++pos;
    }
    return pos;
}

size_t
scanBreakPos(vespalib::string &s, size_t pos)
{
    while (pos < s.size() && s[pos] != ' ' && s[pos] != '\n') {
        ++pos;
    }
    return pos;
}

void
normalizeTimestamp(vespalib::string &s)
{
    size_t pos = findStartPos(s, "ts=");
    if (pos < s.size()) {
        size_t npos = scanBreakPos(s, pos + 3);
        s.replace(pos, npos - pos, "ts=0.0");
        return;
    }
}

void
normalizeAddr(vespalib::string &s, void *addr)
{
    size_t pos = findStartPos(s, "addr=");
    if (pos < s.size()) {
        size_t npos = scanBreakPos(s, pos + 5);
        std::ostringstream os;
        os << "addr=0x";
        os.width(16);
        os.fill('0');
        os << std::hex << reinterpret_cast<unsigned long>(addr);
        s.replace(pos, npos - pos, os.str());
        return;
    }
}

void
normalizeTimestamps(std::vector<vespalib::string> &sv)
{
    for (auto &s : sv) {
        normalizeTimestamp(s);
    }
}

void
normalizeAddrs(std::vector<vespalib::string> &sv, void *addr)
{
    for (auto &s : sv) {
        normalizeAddr(s, addr);
    }
}

}
