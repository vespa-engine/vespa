// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/print/fileconfigformatter.h>
#include <vespa/vespalib/data/slime/slime.h>

using namespace config;
using namespace vespalib::slime::convenience;

TEST("requireThatConfigIsFormatted") {
    ConfigDataBuffer buffer;
    vespalib::Slime & slime(buffer.slimeObject());
    Cursor &c = slime.setObject().setObject("configPayload").setObject("myField");
    c.setString("type", "string");
    c.setString("value", "foo");

    FileConfigFormatter formatter;
    formatter.encode(buffer);
    EXPECT_EQUAL(std::string("myField \"foo\"\n"), buffer.getEncodedString());
}

TEST_MAIN() { TEST_RUN_ALL(); }
