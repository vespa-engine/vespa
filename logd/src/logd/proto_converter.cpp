// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proto_converter.h"
#include <vespa/vespalib/text/utf8.h>

using ns_log::LogMessage;
using ns_log::Logger;

namespace logdemon {

void
ProtoConverter::log_messages_to_proto(const std::vector<LogMessage>& messages, ProtoLogRequest& proto)
{
    for (const auto& message : messages) {
        auto* proto_message = proto.add_log_messages();
        log_message_to_proto(message, *proto_message);
    }
}

namespace {

using ProtoLogLevel = ::logserver::protocol::protobuf::LogMessage_Level;

ProtoLogLevel
convert_level(const Logger::LogLevel& level)
{
    switch (level) {
        case Logger::fatal:
            return ProtoLogLevel::LogMessage_Level_FATAL;
        case Logger::error:
            return ProtoLogLevel::LogMessage_Level_ERROR;
        case Logger::warning:
            return ProtoLogLevel::LogMessage_Level_WARNING;
        case Logger::config:
            return ProtoLogLevel::LogMessage_Level_CONFIG;
        case Logger::info:
            return ProtoLogLevel::LogMessage_Level_INFO;
        case Logger::event:
            return ProtoLogLevel::LogMessage_Level_EVENT;
        case Logger::debug:
            return ProtoLogLevel::LogMessage_Level_DEBUG;
        case Logger::spam:
            return ProtoLogLevel::LogMessage_Level_SPAM;
        case Logger::NUM_LOGLEVELS:
            return ProtoLogLevel::LogMessage_Level_UNKNOWN;
        default:
            return ProtoLogLevel::LogMessage_Level_UNKNOWN;
    }
}

}

void
ProtoConverter::log_message_to_proto(const LogMessage& message, ProtoLogMessage& proto)
{
    proto.set_time_nanos(message.time_nanos());
    proto.set_hostname(message.hostname());
    proto.set_process_id(message.process_id());
    proto.set_thread_id(message.thread_id());
    proto.set_service(message.service());
    proto.set_component(message.component());
    proto.set_level(convert_level(message.level()));
    proto.set_payload(vespalib::Utf8::filter_invalid_sequences(message.payload()));
}

}
