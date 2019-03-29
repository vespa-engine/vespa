// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "forwarder.h"

namespace logdemon {

struct Metrics;

/**
 * Class used to forward log lines to the logserver via a one-way text protocol.
 */
class LegacyForwarder : public Forwarder {
private:
    int _logserverfd;
    Metrics &_metrics;
    ForwardMap _forwardMap;
    int _badLines;
    const char *copystr(const char *b, const char *e) {
        int len = e - b;
        char *ret = new char[len+1];
        strncpy(ret, b, len);
        ret[len] = '\0';
        return ret;
    }
    bool parseLine(std::string_view line);
public:
    LegacyForwarder(Metrics &metrics);
    ~LegacyForwarder();
    void forwardText(const char *text, int len);
    void forwardLine(std::string_view line) override;
    void flush() override {}
    void setForwardMap(const ForwardMap & forwardMap) { _forwardMap = forwardMap; }
    void setLogserverFD(int fd) { _logserverfd = fd; }
    int  getLogserverFD() { return _logserverfd; }
    void sendMode() override;
    int badLines() const override { return _badLines; }
    void resetBadLines() override { _badLines = 0; }
};

}
