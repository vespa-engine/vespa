// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/log/log.h>
#include <vespa/log/reject-filter.h>
#include <string>

namespace ns_log {

class LogTarget;
class LLParser
{
private:
    static const char _hexdigit[17];

    char _defPid[10];
    std::string _defHostname;
    std::string _defService;
    std::string _defComponent;
    Logger::LogLevel _defLevel;

    LogTarget *_target;
    RejectFilter _rejectFilter;
    void makeMessage(const char *tmf, const char *hsf, const char *pdf,
                     const char *svf, const char *cmf, Logger::LogLevel l,
                     char *msg);
    void sendMessage(const char *msg);

public:
    void doInput(char *line);
    LLParser();
    LLParser(const LLParser & ) = delete;
    LLParser& operator = (const LLParser &) = delete;
    ~LLParser();
    void setService(const char *s) { _defService = s; }
    void setComponent(const char *c) { _defComponent = c; }
    void setPid(int p);
    void setDefaultLevel(Logger::LogLevel level) { _defLevel = level; }
};

} // namespace

