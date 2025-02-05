// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/payload_converter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("payload_converter");

using namespace config;
using namespace vespalib;
using namespace vespalib::slime;

TEST(PayloadConverterTest, require_that_v2_payload_leaf_values_can_be_converted_to_cfg_format)
{
    Slime slime;
    Cursor & root(slime.setObject());
    root.setString("foo", "bar");
    root.setLong("bar", 8);
    root.setDouble("baz", 3.1);
    root.setBool("quux", true);
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    std::sort(lines.begin(), lines.end());
    
    ASSERT_EQ(4u, lines.size());
    EXPECT_EQ("bar 8", lines[0]);
    EXPECT_EQ("baz 3.1", lines[1]);
    EXPECT_EQ("foo \"bar\"", lines[2]);
    EXPECT_EQ("quux true", lines[3]);
}

TEST(PayloadConverterTest, require_that_v2_payload_struct_values_can_be_converted_to_cfg_format)
{
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & inner(root.setObject("obj"));
    inner.setString("foo", "bar");
    inner.setLong("bar", 8);
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    std::sort(lines.begin(), lines.end());

    ASSERT_EQ(2u, lines.size());
    EXPECT_EQ("obj.bar 8", lines[0]);
    EXPECT_EQ("obj.foo \"bar\"", lines[1]);
}

TEST(PayloadConverterTest, require_that_v2_payload_array_values_can_be_converted_to_cfg_format)
{
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & inner(root.setArray("arr"));
    inner.addString("foo");
    inner.addLong(8);
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    ASSERT_EQ(2u, lines.size());
    EXPECT_EQ("arr[0] \"foo\"", lines[0]);
    EXPECT_EQ("arr[1] 8", lines[1]);
}


TEST(PayloadConverterTest, require_that_v2_payload_nested_structures_can_be_converted_to_cfg_format)
{
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & inner(root.setArray("arr"));
    Cursor & obj1(inner.addObject());
    Cursor & obj2(inner.addObject());
    obj1.setString("foo", "bar");
    obj2.setLong("bar", 5);
    Cursor & inner2(root.setObject("obj"));
    Cursor & innerArr(inner2.setArray("arr"));
    Cursor & innerobj(innerArr.addObject());
    Cursor & innerArr2(innerobj.setArray("arr2"));
    innerArr2.addString("muhaha");
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    std::sort(lines.begin(), lines.end());
    ASSERT_EQ(3u, lines.size());
    EXPECT_EQ("arr[0].foo \"bar\"", lines[0]);
    EXPECT_EQ("arr[1].bar 5", lines[1]);
    EXPECT_EQ("obj.arr[0].arr2[0] \"muhaha\"", lines[2]);
}

GTEST_MAIN_RUN_ALL_TESTS()
