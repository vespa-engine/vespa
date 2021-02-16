// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_forwarder.h"
#include "metrics.h"
#include <vespa/log/exceptions.h>
#include <vespa/log/log_message.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".logd.empty_forwarder");

using LogLevel = ns_log::Logger::LogLevel;
using ns_log::BadLogLineException;
using ns_log::LogMessage;
using ns_log::Logger;
using LogLevel = Logger::LogLevel;

namespace logdemon {

EmptyForwarder::EmptyForwarder(Metrics& metrics)
    :
      _metrics(metrics),
      _badLines(0)
{
}

EmptyForwarder::~EmptyForwarder() = default;

void
EmptyForwarder::forwardLine(std::string_view line)
{
    assert (line.size() < 1_Mi);

    LogMessage message;
    try {
        message.parse_log_line(line);
    } catch (BadLogLineException& e) {
        LOG(spam, "bad logline: %s", e.what());
        ++_badLines;
        return;
    }

    std::string logLevelName;
    if (message.level() >= LogLevel::NUM_LOGLEVELS) {
        logLevelName = "unknown";
    } else {
        logLevelName = Logger::logLevelNames[message.level()];
    }
    _metrics.countLine(logLevelName, message.service());
}

}
