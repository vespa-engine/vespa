// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-foo.h"
#include <vespa/config/common/configparser.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/misc.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <fstream>

using namespace config;
using vespalib::asciistream;

namespace {

    void writeFile(const std::string & fileName, const std::string & data)
    {
        std::ofstream of;
        of.open(fileName.c_str());
        of << data;
        of.close();
    }

    ConfigValue readConfig(const std::string & fileName)
    {
        asciistream is(asciistream::createFromFile(fileName));
        return ConfigValue(getlines(is), "");
    }
}

TEST(ConfigParserTest, require_that_default_value_exception_provides_error_message)
{
    writeFile("foo.cfg", "blabla foo\n");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_TRUE(false);
    } catch (InvalidConfigException & ice) {
        ASSERT_EQ("Error parsing config 'foo' in namespace 'config': Config parameter fooValue has no default value and is not specified in config", ice.getMessage());
    }
}

TEST(ConfigParserTest, require_that_unknown_fields_can_exist_in_config_payload)
{
    writeFile("foo.cfg", "blablabla foo\nfooValue \"hello\"\n");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_EQ("hello", config.fooValue);
    } catch (InvalidConfigException & ice) {
        ASSERT_FALSE(true);
    }
}

TEST(ConfigParserTest, require_that_required_fields_will_throw_error_with_unknown_fields)
{
    writeFile("foo.cfg", "blablabla foo\nfooValu \"hello\"\n");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_TRUE(false);
    } catch (InvalidConfigException & ice) {
        ASSERT_TRUE(true);
    }
}

TEST(ConfigParserTest, require_that_array_lengths_does_not_have_to_be_specified)
{
    writeFile("foo.cfg", "\nfooValue \"hello\"\nfooArray[0] 3\nfooArray[1] 9\nfooArray[2] 33\nfooStruct[0].innerStruct[0].bar 2\nfooStruct[0].innerStruct[1].bar 3\nfooStruct[1].innerStruct[0].bar 4");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_EQ("hello", config.fooValue);
        ASSERT_EQ(3u, config.fooArray.size());
        ASSERT_EQ(3, config.fooArray[0]);
        ASSERT_EQ(9, config.fooArray[1]);
        ASSERT_EQ(33, config.fooArray[2]);
        ASSERT_EQ(2u, config.fooStruct.size());
        ASSERT_EQ(2u, config.fooStruct[0].innerStruct.size());
        ASSERT_EQ(1u, config.fooStruct[1].innerStruct.size());
        ASSERT_EQ(2, config.fooStruct[0].innerStruct[0].bar);
        ASSERT_EQ(3, config.fooStruct[0].innerStruct[1].bar);
        ASSERT_EQ(4, config.fooStruct[1].innerStruct[0].bar);
    } catch (InvalidConfigException & ice) {
        ASSERT_TRUE(false);
    }
}

TEST(ConfigParserTest, require_that_array_lengths_may_be_specified)
{
    writeFile("foo.cfg", "\nfooValue \"hello\"\nfooArray[3]\nfooArray[0] 3\nfooArray[1] 9\nfooArray[2] 33\nfooStruct[2]\nfooStruct[0].innerStruct[2]\nfooStruct[0].innerStruct[0].bar 2\nfooStruct[0].innerStruct[1].bar 3\nfooStruct[1].innerStruct[1]\nfooStruct[1].innerStruct[0].bar 4");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_EQ("hello", config.fooValue);
        ASSERT_EQ(3u, config.fooArray.size());
        ASSERT_EQ(3, config.fooArray[0]);
        ASSERT_EQ(9, config.fooArray[1]);
        ASSERT_EQ(33, config.fooArray[2]);
        ASSERT_EQ(2u, config.fooStruct[0].innerStruct.size());
        ASSERT_EQ(1u, config.fooStruct[1].innerStruct.size());
        ASSERT_EQ(2, config.fooStruct[0].innerStruct[0].bar);
        ASSERT_EQ(3, config.fooStruct[0].innerStruct[1].bar);
        ASSERT_EQ(4, config.fooStruct[1].innerStruct[0].bar);
    } catch (InvalidConfigException & ice) {
        ASSERT_TRUE(false);
    }
}

TEST(ConfigParserTest, require_that_escaped_values_are_properly_unescaped)
{
    StringVector payload;
    payload.push_back("foo \"a\\nb\\rc\\\\d\\\"e\x42g\"");
    std::string value(ConfigParser::parse<std::string>("foo", payload));
    ASSERT_EQ("a\nb\rc\\d\"eBg", value);
}

TEST(ConfigParserTest, verify_that_locale_does_not_affect_double_parsing)
{
    StringVector payload;
    setlocale(LC_NUMERIC, "nb_NO.UTF-8");
    payload.push_back("foo 3,14");
    VESPA_EXPECT_EXCEPTION(ConfigParser::parse<double>("foo", payload), InvalidConfigException, "Value 3,14 is not a legal double");
    setlocale(LC_NUMERIC, "C");
}

TEST(ConfigParserTest, require_that_maps_can_be_parsed)
{
    writeFile("foo.cfg", "\nfooValue \"a\"\nfooMap{\"foo\"} 1336\nfooMap{\"bar\"} 1337\n");
    FooConfig config(readConfig("foo.cfg"));
    ASSERT_EQ("a", config.fooValue);
    ASSERT_EQ(2u, config.fooMap.size());
    ASSERT_EQ(1336, config.fooMap.at("foo"));
    ASSERT_EQ(1337, config.fooMap.at("bar"));
}

TEST(ConfigParserTest, handles_quotes_for_bool_values)
{
    StringVector payload;
    payload.push_back("foo \"true\"");
    payload.push_back("bar \"123\"");
    payload.push_back("baz \"1234\"");
    payload.push_back("quux \"3.2\"");
    bool b(ConfigParser::parse<bool>("foo", payload));
    int32_t i(ConfigParser::parse<int32_t>("bar", payload));
    int64_t l(ConfigParser::parse<int64_t>("baz", payload));
    double d(ConfigParser::parse<double>("quux", payload));
    EXPECT_EQ(true, b);
    EXPECT_EQ(123, i);
    EXPECT_EQ(1234, l);
    EXPECT_NEAR(3.2, d, 0.001);
}

GTEST_MAIN_RUN_ALL_TESTS()
