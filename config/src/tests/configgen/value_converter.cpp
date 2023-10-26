// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/configgen/value_converter.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <climits>

using namespace config;
using namespace config::internal;
using namespace vespalib;
using namespace vespalib::slime;

struct MyType{
    MyType(const ConfigPayload & payload)
    {
        foo = payload.get()["foo"].asLong();
        bar = payload.get()["bar"].asLong();
    }
    int foo;
    int bar;
};

TEST("that int32_ts are converted") {
    Slime slime;
    Cursor & root = slime.setArray();
    root.addLong(3);
    root.addLong(-2);
    root.addLong(INT_MAX);
    root.addLong(INT_MIN);
    root.addDouble(3.14);
    ValueConverter<int32_t> conv;
    EXPECT_EQUAL(3, conv(root[0]));
    EXPECT_EQUAL(-2, conv(root[1]));
    EXPECT_EQUAL(INT_MAX, conv(root[2]));
    EXPECT_EQUAL(INT_MIN, conv(root[3]));
    EXPECT_EQUAL(3, conv(root[4]));
}

TEST("that int64_ts are converted") {
    Slime slime;
    Cursor & root = slime.setArray();
    root.addLong(3);
    root.addLong(-2);
    root.addLong(LONG_MAX);
    root.addLong(LONG_MIN);
    root.addLong(std::numeric_limits<int64_t>::max());
    root.addLong(std::numeric_limits<int64_t>::min());
    root.addDouble(3.14);
    std::string ref = "{\"val\":9223372036854775807}";
    Slime slime2;
    JsonFormat::decode(ref, slime2);
    EXPECT_EQUAL(std::numeric_limits<int64_t>::max(), slime2.get()["val"].asLong());
    ValueConverter<int64_t> conv;
    EXPECT_EQUAL(3, conv(root[0]));
    EXPECT_EQUAL(-2, conv(root[1]));
    EXPECT_EQUAL(LONG_MAX, conv(root[2]));
    EXPECT_EQUAL(LONG_MIN, conv(root[3]));
    EXPECT_EQUAL(std::numeric_limits<int64_t>::max(), conv(root[4]));
    EXPECT_EQUAL(std::numeric_limits<int64_t>::min(), conv(root[5]));
    EXPECT_EQUAL(3, conv(root[6]));
}

TEST("that values can be parsed as strings") {
    Slime slime;
    Cursor & root = slime.setObject();
    root.setString("intval", "1234");
    root.setString("longval", "42949672969");
    root.setString("boolval", "true");
    root.setString("doubleval", "3.14");
    ValueConverter<int32_t> intConv;
    ValueConverter<int64_t> longConv;
    ValueConverter<bool> boolConv;
    ValueConverter<double> doubleConv;
    EXPECT_EQUAL(1234, intConv(root["intval"]));
    EXPECT_EQUAL(42949672969, longConv(root["longval"]));
    EXPECT_EQUAL(true, boolConv(root["boolval"]));
    EXPECT_APPROX(3.14, doubleConv(root["doubleval"]), 0.0001);
}

TEST("that incompatible types throws exceptions") {
    Slime slime;
    Cursor & root = slime.setObject();
    root.setBool("intval", true);
    root.setBool("longval", true);
    root.setBool("doubleval", true);
    root.setLong("boolval", 3);
    ValueConverter<int32_t> intConv;
    ValueConverter<int64_t> longConv;
    ValueConverter<bool> boolConv;
    ValueConverter<double> doubleConv;
    EXPECT_EXCEPTION(intConv(root["intval"]), InvalidConfigException, "");
    EXPECT_EXCEPTION(longConv(root["longval"]), InvalidConfigException, "");
    EXPECT_EXCEPTION(doubleConv(root["doubleval"]), InvalidConfigException, "");
    EXPECT_EXCEPTION(boolConv(root["boolval"]), InvalidConfigException, "");
}

TEST("that non-valid fields throws exception") {
    Slime slime;
    Cursor & root = slime.setObject();
    ValueConverter<int64_t> conv;
    EXPECT_EXCEPTION(conv("longval", root["longval"]), InvalidConfigException, "Value for 'longval' required but not found");
}

TEST_MAIN() { TEST_RUN_ALL(); }
