// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configparser.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/misc.h>
#include "config-foo.h"
#include <fstream>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace config;
using vespalib::asciistream;

namespace {

    void writeFile(const vespalib::string & fileName, const vespalib::string & data)
    {
        std::ofstream of;
        of.open(fileName.c_str());
        of << data;
        of.close();
    }

    ConfigValue readConfig(const vespalib::string & fileName)
    {
        asciistream is(asciistream::createFromFile(fileName));
        return ConfigValue(getlines(is), "");
    }
}

TEST("require that default value exception provides error message")
{
    writeFile("foo.cfg", "blabla foo\n");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_TRUE(false);
    } catch (InvalidConfigException & ice) {
        ASSERT_EQUAL("Error parsing config 'foo' in namespace 'config': Config parameter fooValue has no default value and is not specified in config", ice.getMessage());
    }
}

TEST("require that unknown fields can exist in config payload")
{
    writeFile("foo.cfg", "blablabla foo\nfooValue \"hello\"\n");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_EQUAL("hello", config.fooValue);
    } catch (InvalidConfigException & ice) {
        ASSERT_FALSE(true);
    }
}

TEST("require that required fields will throw error with unknown fields")
{
    writeFile("foo.cfg", "blablabla foo\nfooValu \"hello\"\n");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_TRUE(false);
    } catch (InvalidConfigException & ice) {
        ASSERT_TRUE(true);
    }
}

TEST("require that array lengths does not have to be specified")
{
    writeFile("foo.cfg", "\nfooValue \"hello\"\nfooArray[0] 3\nfooArray[1] 9\nfooArray[2] 33\nfooStruct[0].innerStruct[0].bar 2\nfooStruct[0].innerStruct[1].bar 3\nfooStruct[1].innerStruct[0].bar 4");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_EQUAL("hello", config.fooValue);
        ASSERT_EQUAL(3u, config.fooArray.size());
        ASSERT_EQUAL(3, config.fooArray[0]);
        ASSERT_EQUAL(9, config.fooArray[1]);
        ASSERT_EQUAL(33, config.fooArray[2]);
        ASSERT_EQUAL(2u, config.fooStruct.size());
        ASSERT_EQUAL(2u, config.fooStruct[0].innerStruct.size());
        ASSERT_EQUAL(1u, config.fooStruct[1].innerStruct.size());
        ASSERT_EQUAL(2, config.fooStruct[0].innerStruct[0].bar);
        ASSERT_EQUAL(3, config.fooStruct[0].innerStruct[1].bar);
        ASSERT_EQUAL(4, config.fooStruct[1].innerStruct[0].bar);
    } catch (InvalidConfigException & ice) {
        ASSERT_TRUE(false);
    }
}

TEST("require that array lengths may be specified")
{
    writeFile("foo.cfg", "\nfooValue \"hello\"\nfooArray[3]\nfooArray[0] 3\nfooArray[1] 9\nfooArray[2] 33\nfooStruct[2]\nfooStruct[0].innerStruct[2]\nfooStruct[0].innerStruct[0].bar 2\nfooStruct[0].innerStruct[1].bar 3\nfooStruct[1].innerStruct[1]\nfooStruct[1].innerStruct[0].bar 4");
    try {
        FooConfig config(readConfig("foo.cfg"));
        ASSERT_EQUAL("hello", config.fooValue);
        ASSERT_EQUAL(3u, config.fooArray.size());
        ASSERT_EQUAL(3, config.fooArray[0]);
        ASSERT_EQUAL(9, config.fooArray[1]);
        ASSERT_EQUAL(33, config.fooArray[2]);
        ASSERT_EQUAL(2u, config.fooStruct[0].innerStruct.size());
        ASSERT_EQUAL(1u, config.fooStruct[1].innerStruct.size());
        ASSERT_EQUAL(2, config.fooStruct[0].innerStruct[0].bar);
        ASSERT_EQUAL(3, config.fooStruct[0].innerStruct[1].bar);
        ASSERT_EQUAL(4, config.fooStruct[1].innerStruct[0].bar);
    } catch (InvalidConfigException & ice) {
        ASSERT_TRUE(false);
    }
}

TEST("require that escaped values are properly unescaped") {
    StringVector payload;
    payload.push_back("foo \"a\\nb\\rc\\\\d\\\"e\x42g\"");
    vespalib::string value(ConfigParser::parse<vespalib::string>("foo", payload));
    ASSERT_EQUAL("a\nb\rc\\d\"eBg", value);
}

TEST("verify that locale does not affect double parsing") {
    StringVector payload;
    setlocale(LC_NUMERIC, "nb_NO.UTF-8");
    payload.push_back("foo 3,14");
    ASSERT_EXCEPTION(ConfigParser::parse<double>("foo", payload), InvalidConfigException, "Value 3,14 is not a legal double");
    setlocale(LC_NUMERIC, "C");
}

TEST("require that maps can be parsed")
{
    writeFile("foo.cfg", "\nfooValue \"a\"\nfooMap{\"foo\"} 1336\nfooMap{\"bar\"} 1337\n");
    FooConfig config(readConfig("foo.cfg"));
    ASSERT_EQUAL("a", config.fooValue);
    ASSERT_EQUAL(2u, config.fooMap.size());
    ASSERT_EQUAL(1336, config.fooMap.at("foo"));
    ASSERT_EQUAL(1337, config.fooMap.at("bar"));
}

TEST("handles quotes for bool values") {
    StringVector payload;
    payload.push_back("foo \"true\"");
    payload.push_back("bar \"123\"");
    payload.push_back("baz \"1234\"");
    payload.push_back("quux \"3.2\"");
    bool b(ConfigParser::parse<bool>("foo", payload));
    int32_t i(ConfigParser::parse<int32_t>("bar", payload));
    int64_t l(ConfigParser::parse<int64_t>("baz", payload));
    double d(ConfigParser::parse<double>("quux", payload));
    EXPECT_EQUAL(true, b);
    EXPECT_EQUAL(123, i);
    EXPECT_EQUAL(1234, l);
    EXPECT_APPROX(3.2, d, 0.001);
}

TEST_MAIN() { TEST_RUN_ALL(); }
