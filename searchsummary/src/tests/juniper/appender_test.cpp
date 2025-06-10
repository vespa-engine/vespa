// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>

#define _NEED_SUMMARY_CONFIG_IMPL
#include <string>
#include <vector>
#include <vespa/juniper/SummaryConfig.h>
#include <vespa/juniper/appender.h>
#include <vespa/juniper/juniperdebug.h>

using namespace juniper;

struct FixtureBase {
    const char*   _connectors;
    SummaryConfig _cfg;
    Appender      _appender;
    FixtureBase(ConfigFlag preserve_white_space)
      : _connectors(""),
        _cfg("[on]", "[off]", "[dots]", "\x1f", reinterpret_cast<const unsigned char*>(_connectors),
             ConfigFlag::CF_OFF, preserve_white_space),
        _appender(&_cfg) {}
    void assertString(const std::string& input, const std::string& output) {
        std::vector<char> buf;
        _appender.append(buf, input.c_str(), input.size());
        EXPECT_EQ(output, std::string(&buf[0], buf.size()));
    }
};

struct DefaultFixture : public FixtureBase {
    DefaultFixture() : FixtureBase(ConfigFlag::CF_OFF) {}
};

struct PreserveFixture : public FixtureBase {
    PreserveFixture() : FixtureBase(ConfigFlag::CF_ON) {}
};

TEST(AppenderTest, requireThatMultipleWhiteSpacesAreEliminated) {
    DefaultFixture f;
    f.assertString("text  with\nwhite \nspace like   this", "text with white space like this");
}

TEST(AppenderTest, requireThatMultipleWhiteSpacesArePreserved) {
    PreserveFixture f;
    f.assertString("text  with\nwhite \nspace like   this", "text  with\nwhite \nspace like   this");
}

GTEST_MAIN_RUN_ALL_TESTS()
