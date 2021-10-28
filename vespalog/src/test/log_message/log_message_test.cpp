// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <gtest/gtest.h>
#include <vespa/log/log_message.h>
#include <vespa/log/exceptions.h>

using LogLevel = ns_log::Logger::LogLevel;

namespace ns_log {

namespace {

void
assertParseFail(std::string exp_what, std::string log_line)
{
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(exp_what, e.what());
    }
}

}

class LogMessageTest : public ::testing::Test {
public:
    LogMessageTest() { }
    ~LogMessageTest() { }
};

TEST_F(LogMessageTest, require_that_plain_entry_is_ok)
{
    std::string log_line = "10.5\tlocalhost\t10/20\ttest\ttestrunner\twarning\thello world";
    LogMessage message;
    message.parse_log_line(log_line);
    EXPECT_EQ(INT64_C(10500000000), message.time_nanos());
    EXPECT_EQ("localhost", message.hostname());
    EXPECT_EQ(10, message.process_id());
    EXPECT_EQ(20, message.thread_id());
    EXPECT_EQ("test", message.service());
    EXPECT_EQ("testrunner", message.component());
    EXPECT_EQ(LogLevel::warning, message.level());
    EXPECT_EQ("hello world", message.payload());
}

TEST_F(LogMessageTest, require_that_missing_thread_id_is_ok)
{
    std::string log_line = "10.5\tlocalhost\t10\ttest\ttestrunner\twarning\thello world";
    LogMessage message;
    message.parse_log_line(log_line);
    EXPECT_EQ(10, message.process_id());
    EXPECT_EQ(0, message.thread_id());
}

TEST_F(LogMessageTest, require_that_empty_line_fails)
{
    assertParseFail("Bad 1st tab: ", "");
}

TEST_F(LogMessageTest, require_that_tab_at_start_of_line_fails)
{
    assertParseFail("Bad 1st tab: \t", "\t");
}

TEST_F(LogMessageTest, require_that_no_tab_after_time_fails)
{
    assertParseFail("Bad 1st tab: 10", "10");
}

TEST_F(LogMessageTest, require_that_malformed_time_fails)
{
    assertParseFail("Bad time field: 10x", "10x\t");
}

TEST_F(LogMessageTest, require_that_no_tab_after_hostname_fails)
{
    std::string log_line = "10\tlocalhost";
    assertParseFail(std::string("Bad 2nd tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_no_tab_after_pid_fails)
{
    std::string log_line = "10\tlocalhost\t10/20";
    assertParseFail(std::string("Bad 3rd tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_malformed_pid_fails)
{
    assertParseFail("Bad pid field: x", "10\tlocalhost\tx\t");
}

TEST_F(LogMessageTest, require_that_malformed_pid_fails_again)
{
    assertParseFail("Bad pid field: 10/", "10\tlocalhost\t10/\t");
}

TEST_F(LogMessageTest, require_that_no_tab_after_service_fails)
{
    std::string log_line = "10\tlocalhost\t10\t";
    assertParseFail(std::string("Bad 4th tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_no_tab_after_component_fails)
{
    std::string log_line = "10\tlocalhost\t10\ttest\t";
    assertParseFail(std::string("Bad 5th tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_empty_component_fails)
{
    std::string log_line = "10\tlocalhost\t10\ttest\t\t";
    assertParseFail(std::string("Bad 5th tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_no_tab_after_level_fails)
{
    std::string log_line = "10\tlocalhost\t10\ttest\ttestrunner\t";
    assertParseFail(std::string("Bad 6th tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_empty_level_fails)
{
    std::string log_line = "10\tlocalhost\t10\ttest\ttestrunner\t\t";
    assertParseFail(std::string("Bad 6th tab: ") + log_line, log_line);
}

TEST_F(LogMessageTest, require_that_empty_payload_is_ok)
{
    std::string log_line = "10\tlocalhost\t10\ttest\ttestrunner\twarning\t";
    LogMessage message;
    message.parse_log_line(log_line);
    EXPECT_EQ(std::string(""), message.payload());
}

TEST_F(LogMessageTest, require_that_nonempty_payload_is_ok)
{
    std::string log_line = "10\tlocalhost\t10\ttest\ttestrunner\twarning\thi";
    LogMessage message;
    message.parse_log_line(log_line);
    EXPECT_EQ(std::string("hi"), message.payload());
}

}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
