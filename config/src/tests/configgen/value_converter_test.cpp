// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/configgen/value_converter.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <climits>

using namespace config;
using namespace config::internal;
using namespace vespalib;
using namespace vespalib::slime;

struct MyType {
    MyType(const ConfigPayload & payload)
    {
        foo = payload.get()["foo"].asLong();
        bar = payload.get()["bar"].asLong();
    }
    int foo;
    int bar;
};

TEST(ValueConverterTest, that_int32_ts_are_converted)
{
    Slime slime;
    Cursor & root = slime.setArray();
    root.addLong(3);
    root.addLong(-2);
    root.addLong(INT_MAX);
    root.addLong(INT_MIN);
    root.addDouble(3.14);
    ValueConverter<int32_t> conv;
    EXPECT_EQ(3, conv(root[0]));
    EXPECT_EQ(-2, conv(root[1]));
    EXPECT_EQ(INT_MAX, conv(root[2]));
    EXPECT_EQ(INT_MIN, conv(root[3]));
    EXPECT_EQ(3, conv(root[4]));
}

TEST(ValueConverterTest, that_int64_ts_are_converted)
{
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
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), slime2.get()["val"].asLong());
    ValueConverter<int64_t> conv;
    EXPECT_EQ(3, conv(root[0]));
    EXPECT_EQ(-2, conv(root[1]));
    EXPECT_EQ(LONG_MAX, conv(root[2]));
    EXPECT_EQ(LONG_MIN, conv(root[3]));
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), conv(root[4]));
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), conv(root[5]));
    EXPECT_EQ(3, conv(root[6]));
}

TEST(ValueConverterTest, that_values_can_be_parsed_as_strings)
{
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
    EXPECT_EQ(1234, intConv(root["intval"]));
    EXPECT_EQ(42949672969, longConv(root["longval"]));
    EXPECT_EQ(true, boolConv(root["boolval"]));
    EXPECT_NEAR(3.14, doubleConv(root["doubleval"]), 0.0001);
}

TEST(ValueConverterTest, that_incompatible_types_throws_exceptions)
{
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
    VESPA_EXPECT_EXCEPTION(intConv(root["intval"]), InvalidConfigException, "");
    VESPA_EXPECT_EXCEPTION(longConv(root["longval"]), InvalidConfigException, "");
    VESPA_EXPECT_EXCEPTION(doubleConv(root["doubleval"]), InvalidConfigException, "");
    VESPA_EXPECT_EXCEPTION(boolConv(root["boolval"]), InvalidConfigException, "");
}

TEST(ValueConverterTest, that_non_valid_fields_throws_exception)
{
    Slime slime;
    Cursor & root = slime.setObject();
    ValueConverter<int64_t> conv;
    VESPA_EXPECT_EXCEPTION(conv("longval", root["longval"]), InvalidConfigException, "Value for 'longval' required but not found");
}

GTEST_MAIN_RUN_ALL_TESTS()
