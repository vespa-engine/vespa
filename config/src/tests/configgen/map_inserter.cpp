// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/config/configgen/map_inserter.hpp>

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

TEST("require that map of ints can be inserted") {
    std::map<vespalib::string, int32_t> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setLong("foo", 3);
    root.setLong("bar", 2);
    root.setLong("baz", 6);
    MapInserter<int32_t> inserter(map);
    root.traverse(inserter);
    ASSERT_EQUAL(3u, map.size());
    ASSERT_EQUAL(3, map["foo"]);
    ASSERT_EQUAL(2, map["bar"]);
    ASSERT_EQUAL(6, map["baz"]);
}

TEST("require that map of struct can be inserted") {
    std::map<vespalib::string, MyType> map;
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
    ASSERT_EQUAL(2u, map.size());
    ASSERT_EQUAL(3, map["foo"].foo);
    ASSERT_EQUAL(4, map["foo"].bar);
    ASSERT_EQUAL(1, map["bar"].foo);
    ASSERT_EQUAL(6, map["bar"].bar);
}

TEST("require that map of long can be inserted") {
    std::map<vespalib::string, int64_t> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setLong("foo", 3);
    root.setLong("bar", 2);
    root.setLong("baz", 6);
    MapInserter<int64_t> inserter(map);
    root.traverse(inserter);
    ASSERT_EQUAL(3u, map.size());
    ASSERT_EQUAL(3, map["foo"]);
    ASSERT_EQUAL(2, map["bar"]);
    ASSERT_EQUAL(6, map["baz"]);
}

TEST("require that map of double can be inserted") {
    std::map<vespalib::string, double> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setDouble("foo", 3.1);
    root.setDouble("bar", 2.4);
    root.setDouble("baz", 6.6);
    MapInserter<double> inserter(map);
    root.traverse(inserter);
    ASSERT_EQUAL(3u, map.size());
    ASSERT_EQUAL(3.1, map["foo"]);
    ASSERT_EQUAL(2.4, map["bar"]);
    ASSERT_EQUAL(6.6, map["baz"]);
}

TEST("require that map of bool can be inserted") {
    std::map<vespalib::string, bool> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setBool("foo", true);
    root.setBool("bar", false);
    root.setBool("baz", true);
    MapInserter<bool> inserter(map);
    root.traverse(inserter);
    ASSERT_EQUAL(3u, map.size());
    ASSERT_TRUE(map["foo"]);
    ASSERT_FALSE(map["bar"]);
    ASSERT_TRUE(map["baz"]);
}

TEST("require that map of string can be inserted") {
    std::map<vespalib::string, vespalib::string> map;
    Slime slime;
    Cursor & root = slime.setObject();
    root.setString("foo", "baz");
    root.setString("bar", "bar");
    root.setString("baz", "foo");
    MapInserter<vespalib::string> inserter(map);
    root.traverse(inserter);
    ASSERT_EQUAL(3u, map.size());
    ASSERT_EQUAL("foo", map["baz"]);
    ASSERT_EQUAL("bar", map["bar"]);
    ASSERT_EQUAL("baz", map["foo"]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
