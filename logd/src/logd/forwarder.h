// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace logdemon {

/**
 * Interface used to forward log lines to something.
 */
class Forwarder {
public:
    virtual ~Forwarder() {}
    virtual void sendMode() = 0;
    virtual void forwardLine(const char *line, const char *eol) = 0;
    virtual int badLines() const = 0;
    virtual void resetBadLines() = 0;
};

}
