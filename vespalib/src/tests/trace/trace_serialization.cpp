// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/trace/tracenode.h>
#include <vespa/vespalib/trace/slime_trace_serializer.h>
#include <vespa/vespalib/trace/slime_trace_deserializer.h>
#include <vespa/vespalib/data/simple_buffer.h>

#include <vespa/log/log.h>
LOG_SETUP("trace_test");

using namespace vespalib;
using namespace vespalib::slime;

TEST(TraceSerializationTest, that_a_single_trace_node_is_serialized) {
    TraceNode node;
    Slime slime;
    SlimeTraceSerializer serializer(slime.setObject());
    node.accept(serializer);
    Inspector & i(slime.get());
    EXPECT_TRUE(i["timestamp"].valid());
    EXPECT_EQ(0l, i["timestamp"].asLong());
    EXPECT_FALSE(i["payload"].valid());
}

constexpr system_time zero_system_time;
constexpr system_time as_ms(long ms) { return system_time(std::chrono::milliseconds(ms)); }


TEST(TraceSerializationTest, that_a_trace_node_with_children_is_serialized) {
    TraceNode node;
    node.addChild("foo", as_ms(1234));
    node.addChild("bar", as_ms(1235));
    Slime slime;
    SlimeTraceSerializer serializer(slime.setObject());
    node.accept(serializer);
    Inspector & i(slime.get());
    EXPECT_TRUE(i["timestamp"].valid());
    EXPECT_EQ(0l, i["timestamp"].asLong());
    EXPECT_TRUE(i["children"].valid());
    Inspector & iBar(i["children"][0]);
    Inspector & iFoo(i["children"][1]);
    EXPECT_TRUE(iFoo.valid());
    EXPECT_TRUE(iBar.valid());
    EXPECT_EQ(1234, iFoo["timestamp"].asLong());
    EXPECT_EQ("foo", iFoo["payload"].asString().make_string());
    EXPECT_EQ(1235, iBar["timestamp"].asLong());
    EXPECT_EQ("bar", iBar["payload"].asString().make_string());
}

TEST(TraceSerializationTest, that_an_empty_root_trace_node_can_be_deserialized) {
    Slime slime;
    Cursor & root(slime.setObject());
    SlimeTraceDeserializer deserializer(root);
    TraceNode node(deserializer.deserialize());
    EXPECT_FALSE(node.hasNote());
    EXPECT_EQ(zero_system_time, node.getTimestamp());
}


TEST(TraceSerializationTest, that_a_single_trace_node_can_be_deserialized) {
    Slime slime;
    Cursor & root(slime.setObject());
    root.setLong("timestamp", 1234);
    root.setString("payload", "hello");
    SlimeTraceDeserializer deserializer(root);
    TraceNode node(deserializer.deserialize());
    EXPECT_EQ(as_ms(1234), node.getTimestamp());
    EXPECT_TRUE(node.hasNote());
    EXPECT_EQ("hello", node.getNote());
}

TEST(TraceSerializationTest, that_a_trace_node_with_children_can_be_deserialized) {
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & rootChildren(root.setArray("children"));
    Cursor & foo(rootChildren.addObject());
    foo.setLong("timestamp", 123);
    Cursor &fooArray(foo.setArray("children"));
    Cursor &foobar(fooArray.addObject());
    foobar.setLong("timestamp", 45);
    foobar.setString("payload", "world");
    Cursor & bar(rootChildren.addObject());
    bar.setLong("timestamp", 67);
    bar.setString("payload", "!");

    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, false);

    SlimeTraceDeserializer deserializer(root);
    TraceNode node(deserializer.deserialize());
    EXPECT_FALSE(node.hasNote());
    ASSERT_EQ(2u, node.getNumChildren());
    TraceNode fooNode(node.getChild(0));
    ASSERT_EQ(1u, fooNode.getNumChildren());
    TraceNode fooBarNode(fooNode.getChild(0));
    EXPECT_EQ("world", fooBarNode.getNote());
    TraceNode barNode(node.getChild(1));
    EXPECT_EQ("!", barNode.getNote());
    ASSERT_EQ(0u, barNode.getNumChildren());
}

TEST(TraceSerializationTest, test_serialization_and_deserialization) {
    TraceNode root;
    root.addChild("foo", as_ms(45));
    root.addChild("bar");
    root.addChild(TraceNode());
    Slime slime;
    SlimeTraceSerializer s(slime.setObject());
    root.accept(s);
    SlimeTraceDeserializer d(slime.get());
    TraceNode root2(d.deserialize());
    ASSERT_EQ(3u, root2.getNumChildren());
}

GTEST_MAIN_RUN_ALL_TESTS()
