// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/log/log.h>
#include <map>
#include <memory>
#include <string_view>

namespace logdemon {

// Mapping saying if a level should be forwarded or not
using ForwardMap = std::map<ns_log::Logger::LogLevel, bool>;

/**
 * Interface used to forward log lines to something.
 */
class Forwarder {
public:
    using UP = std::unique_ptr<Forwarder>;
    virtual ~Forwarder() {}
    virtual void forwardLine(std::string_view log_line) = 0;
    virtual void flush() = 0;
    virtual int badLines() const = 0;
    virtual void resetBadLines() = 0;
};

}
