// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <gtest/gtest.h>
#include <vespa/log/log_message.h>
#include <vespa/log/exceptions.h>

using LogLevel = ns_log::Logger::LogLevel;

namespace ns_log {

namespace {

std::ostream &
add_time(std::ostream &os, double now)
{
    constexpr int64_t onegig = 1000000000;
    int64_t now64 = now * onegig;
    int64_t now64i = now64 / onegig;
    int64_t now64r = now64 % onegig;
    os << now64i << '.' << std::setw(6) << std::setfill('0') << (now64r / 1000) <<
        std::setw(0) << std::setfill(' ');
    return os;
}

std::string
build_logline(double now, std::string hostname, std::string pidString, std::string service, std::string component, std::string level, std::string payload)
{
    std::ostringstream os;
    add_time(os, now) << '\t' << hostname << '\t' << pidString << '\t' <<
        service << '\t' << component << '\t' << level << '\t' << payload;
    return os.str();
}

std::string
build_logline(double now, std::string remaining)
{
    std::ostringstream os;
    add_time(os, now) << '\t' << remaining;
    return os.str();
}

}

class LogMessageTest : public ::testing::Test {
protected:
    double _now;
public:
    LogMessageTest()
        : _now(0.0)
    {
        _now = time(nullptr);
    }
    ~LogMessageTest() { }
};

TEST_F(LogMessageTest, require_that_plain_entry_is_ok)
{
    std::string log_line = build_logline(_now, "localhost", "10/20", "test", "testrunner", "warning", "hello world");
    LogMessage message;
    message.parse_log_line(log_line);
    EXPECT_EQ(int64_t(_now * 1000000000), message.time_nanos());
    EXPECT_EQ("localhost", message.hostname());
    EXPECT_EQ(10, message.process_id());
    EXPECT_EQ(20, message.thread_id());
    EXPECT_EQ("test", message.service());
    EXPECT_EQ("testrunner", message.component());
    EXPECT_EQ(LogLevel::warning, message.level());
    EXPECT_EQ("hello world", message.payload());
}

TEST_F(LogMessageTest, require_that_tab_at_start_of_line_fails)
{
    std::string log_line = "\t";
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 1st tab: \t"), e.what());
    }
}

TEST_F(LogMessageTest, require_that_no_tab_after_time_fails)
{
    std::string log_line = "10";
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 1st tab: 10"), e.what());
    }
}

TEST_F(LogMessageTest, require_that_malformed_time_fails)
{
    std::string log_line = "10x\t";
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad time field: 10x"), e.what());
    }
}

TEST_F(LogMessageTest, require_that_very_old_time_fails)
{
    std::string log_line = build_logline(_now - 101 * 24 * 3600, "");
    std::string time_field = log_line.substr(0, log_line.size() - 1);
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("time > 100 days in the past: ") + time_field, e.what());
    }
}

TEST_F(LogMessageTest, require_that_very_future_time_fails)
{
    std::string log_line = build_logline(_now + 11 * 24 * 3600, "");
    std::string time_field = log_line.substr(0, log_line.size() - 1);
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("time > 10 days in the future: ") + time_field, e.what());
    }
}

TEST_F(LogMessageTest, require_that_no_tab_after_hostname_fails)
{
    std::string log_line = build_logline(_now, "localhost");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 2nd tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_no_tab_after_pid_fails)
{
    std::string log_line = build_logline(_now, "localhost\t10/20");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 3rd tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_malformed_pid_fails)
{
    std::string log_line = build_logline(_now, "localhost\tx\t");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad pid field: x"), e.what());
    }
}

TEST_F(LogMessageTest, require_that_no_tab_after_service_fails)
{
    std::string log_line = build_logline(_now, "localhost\t10\t");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 4th tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_no_tab_after_component_fails)
{
    std::string log_line = build_logline(_now, "localhost\t10\ttest\t");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 5th tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_empty_component_fails)
{
    std::string log_line = build_logline(_now, "localhost\t10\ttest\t\t");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 5th tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_no_tab_after_level_fails)
{
    std::string log_line = build_logline(_now, "localhost\t10\ttest\ttestrunner\t");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 6th tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_empty_level_fails)
{
    std::string log_line = build_logline(_now, "localhost\t10\ttest\ttestrunner\t\t");
    LogMessage message;
    try {
        message.parse_log_line(log_line);
        EXPECT_TRUE(false) << "Exception not thrown";
    } catch (BadLogLineException &e) {
        EXPECT_EQ(std::string("Bad 6th tab: ") + log_line, e.what());
    }
}

TEST_F(LogMessageTest, require_that_empty_payload_is_ok)
{
    std::string log_line = build_logline(_now, "localhost\t10\ttest\ttestrunner\twarning\t");
    LogMessage message;
    message.parse_log_line(log_line);
    EXPECT_EQ(10, message.process_id());
    EXPECT_EQ(0, message.thread_id());
    EXPECT_EQ(std::string(""), message.payload());
}

TEST_F(LogMessageTest, require_that_nonempty_payload_is_ok)
{
    std::string log_line = build_logline(_now, "localhost\t10\ttest\ttestrunner\twarning\thi");
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
