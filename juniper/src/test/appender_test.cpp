// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#define _NEED_SUMMARY_CONFIG_IMPL
#include <vespa/juniper/SummaryConfig.h>
#include <vespa/juniper/juniperdebug.h>
#include <vespa/juniper/appender.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

using namespace juniper;

struct FixtureBase
{
    const char *_connectors;
    SummaryConfig _cfg;
    Appender _appender;
    FixtureBase(ConfigFlag preserve_white_space)
        : _connectors(""),
          _cfg("[on]", "[off]", "[dots]", "\x1f",
                  reinterpret_cast<const unsigned char*>(_connectors),
                  ConfigFlag::CF_OFF,
                  preserve_white_space),
          _appender(&_cfg)
    {
    }
    void assertString(const vespalib::string &input, const vespalib::string &output) {
        std::vector<char> buf;
        _appender.append(buf, input.c_str(), input.size());
        EXPECT_EQUAL(output, vespalib::string(&buf[0], buf.size()));
    }
};

struct DefaultFixture : public FixtureBase
{
    DefaultFixture() : FixtureBase(ConfigFlag::CF_OFF) {}
};

struct PreserveFixture : public FixtureBase
{
    PreserveFixture() : FixtureBase(ConfigFlag::CF_ON) {}
};

TEST_F("requireThatMultipleWhiteSpacesAreEliminated", DefaultFixture)
{
    f.assertString("text  with\nwhite \nspace like   this",
                   "text with white space like this");
}

TEST_F("requireThatMultipleWhiteSpacesArePreserved", PreserveFixture)
{
    f.assertString("text  with\nwhite \nspace like   this",
                   "text  with\nwhite \nspace like   this");
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
