// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "log_protocol_proto.h"
#include <vespa/log/log_message.h>
#include <vector>

namespace logdemon {

/**
 * Contains functions to convert log messages to protobuf objects.
 */
struct ProtoConverter {
    using ProtoLogRequest = logserver::protocol::protobuf::LogRequest;
    using ProtoLogResponse = logserver::protocol::protobuf::LogResponse;
    using ProtoLogMessage = logserver::protocol::protobuf::LogMessage;

    static void log_messages_to_proto(const std::vector<ns_log::LogMessage>& messages, ProtoLogRequest& proto);
    static void log_message_to_proto(const ns_log::LogMessage& message, ProtoLogMessage& proto);
};

}
