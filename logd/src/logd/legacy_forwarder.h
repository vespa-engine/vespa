// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "forwarder.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace logdemon {

struct Metrics;

/**
 * Class used to forward log lines to the logserver via a one-way text protocol.
 */
class LegacyForwarder : public Forwarder {
private:
    Metrics &_metrics;
    int _logserver_fd;
    ForwardMap _forwardMap;
    int _badLines;
    const char *copystr(const char *b, const char *e) {
        int len = e - b;
        char *ret = new char[len+1];
        strncpy(ret, b, len);
        ret[len] = '\0';
        return ret;
    }
    void connect_to_logserver(const vespalib::string& logserver_host, int logserver_port);
    void connect_to_dev_null();
    bool parseLine(std::string_view line);
    void forwardText(const char *text, int len);
    LegacyForwarder(Metrics &metrics);

public:
    using UP = std::unique_ptr<LegacyForwarder>;
    static LegacyForwarder::UP to_logserver(Metrics& metrics, const vespalib::string& logserver_host, int logserver_port);
    static LegacyForwarder::UP to_dev_null(Metrics& metrics);
    static LegacyForwarder::UP to_open_file(Metrics& metrics, int file_desc);
    ~LegacyForwarder();
    void setForwardMap(const ForwardMap& forwardMap) { _forwardMap = forwardMap; }

    // Implements Forwarder
    void forwardLine(std::string_view line) override;
    void flush() override {}
    void sendMode() override;
    int badLines() const override { return _badLines; }
    void resetBadLines() override { _badLines = 0; }
};

}
