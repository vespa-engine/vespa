// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include "service.h"
#include <vespa/vespalib/util/hashmap.h>
#include <map>

namespace logdemon {

typedef vespalib::HashMap<bool> SeenMap;
// Mapping saying if a level should be forwarded or not
typedef std::map<ns_log::Logger::LogLevel, bool> ForwardMap;

class LevelParser
{
private:
    SeenMap _seenLevelMap;
public:
    ns_log::Logger::LogLevel parseLevel(const char *level);
    LevelParser() : _seenLevelMap(false) {}
};

class Forwarder
{
private:
    int _logserverfd;
    ForwardMap _forwardMap;
    LevelParser _levelparser;
    const char *copystr(const char *b, const char *e) {
        int len = e - b;
        char *ret = new char[len+1];
        strncpy(ret, b, len);
        ret[len] = '\0';
        return ret;
    }
    bool parseline(const char *linestart, const char *lineend);
public:
    Services knownServices;
    int _badLines;
    Forwarder();
    ~Forwarder();
    void forwardText(const char *text, int len);
    void forwardLine(const char *line, const char *eol);
    void setForwardMap(const ForwardMap & forwardMap) { _forwardMap = forwardMap; }
    void setLogserverFD(int fd) { _logserverfd = fd; }
    int  getLogserverFD() { return _logserverfd; }
    void sendMode();
};

} // namespace
