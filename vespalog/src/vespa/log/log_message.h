// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "log.h"
#include <string_view>
#include <string>

namespace ns_log {

/*
 * Class containing a log message.
 */
class LogMessage {
    int64_t          _time_nanos;
    std::string      _hostname;
    int32_t          _process_id;
    int32_t          _thread_id;
    std::string      _service;
    std::string      _component;
    Logger::LogLevel _level;
    std::string      _payload;

public:
    LogMessage();
    LogMessage(int64_t time_nanos_in,
               const std::string& hostname_in,
               int32_t process_id_in,
               int32_t thread_id_in,
               const std::string& service_in,
               const std::string& component_in,
               Logger::LogLevel level_in,
               const std::string& payload_in);
    ~LogMessage();
    void parse_log_line(std::string_view log_line);
    int64_t           time_nanos() const { return _time_nanos; }
    const std::string  &hostname() const { return _hostname; }
    int32_t           process_id() const { return _process_id; }
    int32_t            thread_id() const { return _thread_id; }
    const std::string   &service() const { return _service;}
    const std::string &component() const { return _component; }
    Logger::LogLevel       level() const { return _level; }
    const std::string   &payload() const { return _payload; }
};

}
