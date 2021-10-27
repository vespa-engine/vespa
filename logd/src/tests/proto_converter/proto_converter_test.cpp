// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <logd/proto_converter.h>
#include <vespa/vespalib/gtest/gtest.h>

using ns_log::Logger;
using ns_log::LogMessage;

using Converter = logdemon::ProtoConverter;
using ProtoLogLevel = logserver::protocol::protobuf::LogMessage_Level;

struct LogMessageTest : public ::testing::Test {
    LogMessage message;
    Converter::ProtoLogMessage proto;
    void convert() {
        Converter::log_message_to_proto(message, proto);
    }
    void expect_log_level_converted(ProtoLogLevel proto_level, Logger::LogLevel message_level) {
       message = LogMessage(1, "", 1, 1, "", "", message_level, "");
       convert();
       EXPECT_EQ(proto_level, proto.level());
    }
};

void
expect_proto_log_message_equal(int64_t exp_time_nanos,
                               const std::string& exp_hostname,
                               int32_t exp_process_id,
                               int32_t exp_thread_id,
                               const std::string& exp_service,
                               const std::string& exp_component,
                               ProtoLogLevel exp_level,
                               const std::string& exp_payload,
                               const Converter::ProtoLogMessage& proto)
{
    EXPECT_EQ(exp_time_nanos, proto.time_nanos());
    EXPECT_EQ(exp_hostname, proto.hostname());
    EXPECT_EQ(exp_process_id, proto.process_id());
    EXPECT_EQ(exp_thread_id, proto.thread_id());
    EXPECT_EQ(exp_service, proto.service());
    EXPECT_EQ(exp_component, proto.component());
    EXPECT_EQ(exp_level, proto.level());
    EXPECT_EQ(exp_payload, proto.payload());
}

TEST_F(LogMessageTest, log_message_is_converted)
{
    message = LogMessage(12345, "foo_host", 3, 5, "foo_service", "foo_component", Logger::info, "foo_payload");
    convert();
    expect_proto_log_message_equal(12345, "foo_host", 3, 5, "foo_service", "foo_component",
                                   ProtoLogLevel::LogMessage_Level_INFO, "foo_payload", proto);
}

TEST_F(LogMessageTest, log_levels_are_converted)
{
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_FATAL, Logger::fatal);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_ERROR, Logger::error);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_WARNING, Logger::warning);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_CONFIG, Logger::config);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_INFO, Logger::info);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_EVENT, Logger::event);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_DEBUG, Logger::debug);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_SPAM, Logger::spam);
    expect_log_level_converted(ProtoLogLevel::LogMessage_Level_UNKNOWN, Logger::NUM_LOGLEVELS);
}

struct LogRequestTest : public ::testing::Test {
    std::vector<LogMessage> messages;
    Converter::ProtoLogRequest proto;
    void convert() {
        Converter::log_messages_to_proto(messages, proto);
    }
};

TEST_F(LogRequestTest, log_messages_are_converted_to_request)
{
    messages.emplace_back(12345, "foo_host", 3, 5, "foo_service", "foo_component", Logger::info, "foo_payload");
    messages.emplace_back(54321, "bar_host", 7, 9, "bar_service", "bar_component", Logger::event, "bar_payload");
    convert();
    EXPECT_EQ(2, proto.log_messages_size());
    expect_proto_log_message_equal(12345, "foo_host", 3, 5, "foo_service", "foo_component",
                                   ProtoLogLevel::LogMessage_Level_INFO, "foo_payload", proto.log_messages(0));
    expect_proto_log_message_equal(54321, "bar_host", 7, 9, "bar_service", "bar_component",
                                   ProtoLogLevel::LogMessage_Level_EVENT, "bar_payload", proto.log_messages(1));
}

// UTF-8 encoding of \U+FFFD
#define FFFD "\xEF\xBF\xBD"

TEST_F(LogRequestTest, invalid_utf8_is_filtered)
{
    messages.emplace_back(12345, "foo_host", 3, 5, "foo_service", "foo_component", Logger::info,
        "valid: \xE2\x82\xAC and \xEF\xBF\xBA; semi-valid: \xED\xA0\xBD\xED\xB8\x80; invalid: \xCC surrogate \xED\xBF\xBF overlong \xC1\x81 end"
    );
    convert();
    EXPECT_EQ(1, proto.log_messages_size());
    expect_proto_log_message_equal(12345, "foo_host", 3, 5, "foo_service", "foo_component",
        ProtoLogLevel::LogMessage_Level_INFO,
        "valid: \xE2\x82\xAC and \xEF\xBF\xBA; semi-valid: " FFFD FFFD "; invalid: " FFFD " surrogate " FFFD " overlong " FFFD FFFD " end",
        proto.log_messages(0));
}

GTEST_MAIN_RUN_ALL_TESTS()

