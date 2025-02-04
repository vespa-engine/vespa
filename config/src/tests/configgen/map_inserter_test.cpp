// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/config/configgen/map_inserter.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;
using namespace config::internal;
using namespace vespalib;
using namespace vespalib::slime;

struct MyType{
    MyType() : foo(0), bar(0) {}
    MyType(const ConfigPayload & payload)
    {
        foo = payload.get()["foo"].asLong();
        bar = payload.get()["bar"].asLong();
    }
    int foo;
    int bar;
};

TEST(MapInserterTest, require_that_map_of_ints_can_be_inserted)
{
    std::map<std::string, int32_t> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setLong("foo", 3);
    root.setLong("bar", 2);
    root.setLong("baz", 6);
    MapInserter<int32_t> inserter(map);
    root.traverse(inserter);
    ASSERT_EQ(3u, map.size());
    ASSERT_EQ(3, map["foo"]);
    ASSERT_EQ(2, map["bar"]);
    ASSERT_EQ(6, map["baz"]);
}

TEST(MapInserterTest, require_that_map_of_struct_can_be_inserted)
{
    std::map<std::string, MyType> map;
    Slime slime;
    Cursor & root = slime.setObject();
    Cursor & one = root.setObject("foo");
    one.setLong("foo", 3);
    one.setLong("bar", 4);
    Cursor & two = root.setObject("bar");
    two.setLong("foo", 1);
    two.setLong("bar", 6);
    MapInserter<MyType> inserter(map);
    root.traverse(inserter);
    ASSERT_EQ(2u, map.size());
    ASSERT_EQ(3, map["foo"].foo);
    ASSERT_EQ(4, map["foo"].bar);
    ASSERT_EQ(1, map["bar"].foo);
    ASSERT_EQ(6, map["bar"].bar);
}

TEST(MapInserterTest, require_that_map_of_long_can_be_inserted)
{
    std::map<std::string, int64_t> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setLong("foo", 3);
    root.setLong("bar", 2);
    root.setLong("baz", 6);
    MapInserter<int64_t> inserter(map);
    root.traverse(inserter);
    ASSERT_EQ(3u, map.size());
    ASSERT_EQ(3, map["foo"]);
    ASSERT_EQ(2, map["bar"]);
    ASSERT_EQ(6, map["baz"]);
}

TEST(MapInserterTest, require_that_map_of_double_can_be_inserted)
{
    std::map<std::string, double> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setDouble("foo", 3.1);
    root.setDouble("bar", 2.4);
    root.setDouble("baz", 6.6);
    MapInserter<double> inserter(map);
    root.traverse(inserter);
    ASSERT_EQ(3u, map.size());
    ASSERT_EQ(3.1, map["foo"]);
    ASSERT_EQ(2.4, map["bar"]);
    ASSERT_EQ(6.6, map["baz"]);
}

TEST(MapInserterTest, require_that_map_of_bool_can_be_inserted)
{
    std::map<std::string, bool> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setBool("foo", true);
    root.setBool("bar", false);
    root.setBool("baz", true);
    MapInserter<bool> inserter(map);
    root.traverse(inserter);
    ASSERT_EQ(3u, map.size());
    ASSERT_TRUE(map["foo"]);
    ASSERT_FALSE(map["bar"]);
    ASSERT_TRUE(map["baz"]);
}

TEST(MapInserterTest, require_that_map_of_string_can_be_inserted)
{
    std::map<std::string, std::string> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setString("foo", "baz");
    root.setString("bar", "bar");
    root.setString("baz", "foo");
    MapInserter<std::string> inserter(map);
    root.traverse(inserter);
    ASSERT_EQ(3u, map.size());
    ASSERT_EQ("foo", map["baz"]);
    ASSERT_EQ("bar", map["bar"]);
    ASSERT_EQ("baz", map["foo"]);
}

GTEST_MAIN_RUN_ALL_TESTS()
