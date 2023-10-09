// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <cstring>
#include <cstdlib>

#include "reject-filter.h"

namespace ns_log {

void
RejectFilter::addRejectRule(Logger::LogLevel level, const std::string & message)
{
    _rejectRules.push_back(RejectRule(level, message, false));
}

void
RejectFilter::addExactRejectRule(Logger::LogLevel level, const std::string & message)
{
    _rejectRules.push_back(RejectRule(level, message, true));
}

bool
RejectFilter::shouldReject(Logger::LogLevel level, const char * message)
{
    if (message == NULL) {
        return false;
    }
    for (size_t i = 0; i < _rejectRules.size(); i++) {
        if (_rejectRules[i].shouldReject(level, message)) {
            return true;
        }
    }

        
    return false;
}

bool
RejectFilter::RejectRule::shouldReject(Logger::LogLevel level, const char * message)
{
    if (_level == level) {
        if (_exact) {
            if (strlen(message) == _message.length() && _message.compare(message) == 0) {
                return true;
            }
        } else {
            if (strstr(message, _message.c_str()) != NULL) {
                return true;
            }
        }
    }
    return false;
}

RejectFilter
RejectFilter::createDefaultFilter()
{
    RejectFilter filter;
    filter.addRejectRule(Logger::warning, "Using FILTER_NONE:  This must be paranoid approved, and since you are using FILTER_NONE you must live with this error.");
    filter.addExactRejectRule(Logger::warning, "");
    filter.addRejectRule(Logger::warning, "yjava_preload.so: [preload.c:670] Accept failed: -1 (4)");
    return filter;
}

} // end namespace ns_log
