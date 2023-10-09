// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "forwarder.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace logdemon {

struct Metrics;

/**
 * Class that does not forward log lines, but tracks metrics.
 *
 * Used when forwarding to logserver is turned off.
 */
class EmptyForwarder : public Forwarder {
private:
    Metrics& _metrics;
    int _badLines;

public:
    EmptyForwarder(Metrics& metrics);
    ~EmptyForwarder();

    // Implements Forwarder
    void forwardLine(std::string_view line) override;
    void flush() override {}
    int badLines() const override { return _badLines; }
    void resetBadLines() override { _badLines = 0; }
};

}
