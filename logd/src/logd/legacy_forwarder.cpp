// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include "legacy_forwarder.h"
#include "metrics.h"
#include <vespa/log/log_message.h>
#include <vespa/log/exceptions.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/locale/c.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("");

using LogLevel = ns_log::Logger::LogLevel;
using ns_log::BadLogLineException;
using ns_log::LogMessage;
using ns_log::Logger;
using LogLevel = Logger::LogLevel;

namespace logdemon {

LegacyForwarder::LegacyForwarder(Metrics &metrics)
    : _logserverfd(-1),
      _metrics(metrics),
      _forwardMap(),
      _badLines(0)
{}
LegacyForwarder::~LegacyForwarder() = default;

void
LegacyForwarder::forwardText(const char *text, int len)
{
    int wsize = write(_logserverfd, text, len);

    if (wsize != len) {
        if (wsize > 0) {
            LOG(warning, "only wrote %d of %d bytes to logserver", wsize, len);
        } else {
            LOG(warning, "problem sending data to logserver: %s", strerror(errno));
        }

        throw ConnectionException("problem sending data");
    }
}

void
LegacyForwarder::sendMode()
{
    char buf[1024];
    snprintf(buf, 1024, "mode logd %s\n", vespalib::VersionTag);
    int len = strlen(buf);
    if (len < 100) {
        forwardText(buf, len);
    } else {
        LOG(warning, "too long mode line: %s", buf);
    }
}

void
LegacyForwarder::forwardLine(const char *line, const char *eol)
{
    int linelen = eol - line;

    assert(_logserverfd >= 0);
    assert (linelen > 0);
    assert (linelen < 1024*1024);
    assert (line[linelen - 1] == '\n');

    if (parseline(line, eol)) {
        forwardText(line, linelen);
    }
}

bool
LegacyForwarder::parseline(const char *linestart, const char *lineend)
{
    LogMessage message;
    try {
        message.parse_log_line(std::string_view(linestart, lineend - linestart));
    } catch (BadLogLineException &e) {
        LOG(spam, "bad logline: %s", e.what());
        ++_badLines;
        return false;
    }

    std::string logLevelName;
    if (message.level() >= LogLevel::NUM_LOGLEVELS) {
        logLevelName = "unknown";
    } else {
        logLevelName = Logger::logLevelNames[message.level()];
    }
    _metrics.countLine(logLevelName, message.service());

    // Check overrides
    ForwardMap::iterator found = _forwardMap.find(message.level());
    if (found != _forwardMap.end()) {
        return found->second;
    }
    return false; // Unknown log level
}


} // namespace
