// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/print/fileconfigformatter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;
using namespace vespalib::slime::convenience;

TEST(ConfigFormatTest, requireThatConfigIsFormatted)
{
    ConfigDataBuffer buffer;
    vespalib::Slime & slime(buffer.slimeObject());
    Cursor &c = slime.setObject().setObject("configPayload").setObject("myField");
    c.setString("type", "string");
    c.setString("value", "foo");

    FileConfigFormatter formatter;
    formatter.encode(buffer);
    EXPECT_EQ(std::string("myField \"foo\"\n"), buffer.getEncodedString());
}

GTEST_MAIN_RUN_ALL_TESTS()
