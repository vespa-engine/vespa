// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "conn.h"
#include "exceptions.h"
#include "legacy_forwarder.h"
#include "metrics.h"
#include <vespa/log/log_message.h>
#include <vespa/log/exceptions.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <fcntl.h>
#include <unistd.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("");

using LogLevel = ns_log::Logger::LogLevel;
using ns_log::BadLogLineException;
using ns_log::LogMessage;
using ns_log::Logger;
using LogLevel = Logger::LogLevel;
using vespalib::make_string;

namespace logdemon {

void
LegacyForwarder::connect_to_logserver(const vespalib::string& logserver_host, int logserver_port)
{
    int new_fd = makeconn(logserver_host.c_str(), logserver_port);
    if (new_fd >= 0) {
        LOG(debug, "Connected to logserver at %s:%d", logserver_host.c_str(), logserver_port);
        _logserver_fd = new_fd;
    } else {
        auto error_msg = make_string("Could not connect to %s:%d", logserver_host.c_str(), logserver_port);
        LOG(debug, "%s", error_msg.c_str());
        throw ConnectionException(error_msg);
    }
}

void
LegacyForwarder::connect_to_dev_null()
{
    int new_fd = open("/dev/null", O_RDWR);
    if (new_fd >= 0) {
        LOG(debug, "Opened /dev/null for read/write");
        _logserver_fd = new_fd;
    } else {
        auto error_msg = make_string("Error opening /dev/null (%d): %s", new_fd, strerror(new_fd));
        LOG(debug, "%s", error_msg.c_str());
        throw ConnectionException(error_msg);
    }
}

LegacyForwarder::LegacyForwarder(Metrics &metrics, const ForwardMap& forward_filter)
    :
      _metrics(metrics),
      _logserver_fd(-1),
      _forward_filter(forward_filter),
      _badLines(0)
{
}

LegacyForwarder::UP
LegacyForwarder::to_logserver(Metrics& metrics, const ForwardMap& forward_filter,
                              const vespalib::string& logserver_host, int logserver_port)
{
    LegacyForwarder::UP result(new LegacyForwarder(metrics, forward_filter));
    result->connect_to_logserver(logserver_host, logserver_port);
    return result;
}

LegacyForwarder::UP
LegacyForwarder::to_dev_null(Metrics& metrics)
{
    LegacyForwarder::UP result(new LegacyForwarder(metrics, ForwardMap()));
    result->connect_to_dev_null();
    return result;
}

LegacyForwarder::UP
LegacyForwarder::to_open_file(Metrics& metrics, const ForwardMap& forward_filter, int file_desc)
{
    LegacyForwarder::UP result(new LegacyForwarder(metrics, forward_filter));
    result->_logserver_fd = file_desc;
    return result;
}

LegacyForwarder::~LegacyForwarder()
{
    if (_logserver_fd >= 0) {
        close(_logserver_fd);
    }
}

void
LegacyForwarder::forwardText(const char *text, int len)
{
    int wsize = write(_logserver_fd, text, len);

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
LegacyForwarder::forwardLine(std::string_view line)
{
    assert(_logserver_fd >= 0);
    assert (line.size() < 1024*1024);

    if (parseLine(line)) {
        std::ostringstream line_copy;
        line_copy << line << std::endl;
        forwardText(line_copy.str().data(), line_copy.str().size());
    }
}

bool
LegacyForwarder::parseLine(std::string_view line)
{
    LogMessage message;
    try {
        message.parse_log_line(line);
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
    auto found = _forward_filter.find(message.level());
    if (found != _forward_filter.end()) {
        return found->second;
    }
    return false; // Unknown log level
}


} // namespace
