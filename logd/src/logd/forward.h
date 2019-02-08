// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "service.h"
#include <map>
#include <unordered_set>

namespace logdemon {

using SeenMap = std::unordered_set<std::string>;
// Mapping saying if a level should be forwarded or not
using ForwardMap = std::map<ns_log::Logger::LogLevel, bool>;

struct Metrics;

class LevelParser
{
private:
    SeenMap _seenLevelMap;
public:
    ns_log::Logger::LogLevel parseLevel(const char *level);
    LevelParser() : _seenLevelMap() {}
};

class Forwarder
{
private:
    int _logserverfd;
    Metrics &_metrics;
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
    Forwarder(Metrics &metrics);
    ~Forwarder();
    void forwardText(const char *text, int len);
    void forwardLine(const char *line, const char *eol);
    void setForwardMap(const ForwardMap & forwardMap) { _forwardMap = forwardMap; }
    void setLogserverFD(int fd) { _logserverfd = fd; }
    int  getLogserverFD() { return _logserverfd; }
    void sendMode();
};

}
