// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/log/log.h>

#include <vector>
#include <string>

namespace ns_log {

/**
 * A reject filter is simply a list of log messages that should be rejected and
 * not logged.
 */
class RejectFilter {
private:
    class RejectRule {
    private:
        Logger::LogLevel _level;
        std::string _message;
        bool _exact;
    public:
        RejectRule(Logger::LogLevel level, const std::string & message, bool exact)
            : _level(level), _message(message), _exact(exact)
        { }
        bool shouldReject(Logger::LogLevel level, const char * message);
    };
    std::vector<RejectRule> _rejectRules;
public:
    void addRejectRule(Logger::LogLevel level, const std::string & rejectedMessage);
    void addExactRejectRule(Logger::LogLevel level, const std::string & rejectedMessage);
    bool shouldReject(Logger::LogLevel level, const char * message);
    static RejectFilter createDefaultFilter();
};

} // end namespace ns_log

