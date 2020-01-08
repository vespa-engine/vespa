// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/trace/tracenode.h>
#include <vespa/vespalib/trace/slime_trace_serializer.h>
#include <vespa/vespalib/trace/slime_trace_deserializer.h>

#include <vespa/log/log.h>
LOG_SETUP("trace_test");

using namespace vespalib;
using namespace vespalib::slime;

TEST("that a single trace node is serialized") {
    TraceNode node;
    Slime slime;
    SlimeTraceSerializer serializer(slime.setObject());
    node.accept(serializer);
    Inspector & i(slime.get());
    EXPECT_TRUE(i["timestamp"].valid());
    EXPECT_EQUAL(0l, i["timestamp"].asLong());
    EXPECT_FALSE(i["payload"].valid());
}

constexpr system_time zero_system_time;
constexpr system_time as_ms(long ms) { return system_time(std::chrono::milliseconds(ms)); }


TEST("that a trace node with children is serialized") {
    TraceNode node;
    node.addChild("foo", as_ms(1234));
    node.addChild("bar", as_ms(1235));
    Slime slime;
    SlimeTraceSerializer serializer(slime.setObject());
    node.accept(serializer);
    Inspector & i(slime.get());
    EXPECT_TRUE(i["timestamp"].valid());
    EXPECT_EQUAL(0l, i["timestamp"].asLong());
    EXPECT_TRUE(i["children"].valid());
    Inspector & iBar(i["children"][0]);
    Inspector & iFoo(i["children"][1]);
    EXPECT_TRUE(iFoo.valid());
    EXPECT_TRUE(iBar.valid());
    EXPECT_EQUAL(1234, iFoo["timestamp"].asLong());
    EXPECT_EQUAL("foo", iFoo["payload"].asString().make_string());
    EXPECT_EQUAL(1235, iBar["timestamp"].asLong());
    EXPECT_EQUAL("bar", iBar["payload"].asString().make_string());
}

TEST("that an empty root trace node can be deserialized") {
    Slime slime;
    Cursor & root(slime.setObject());
    SlimeTraceDeserializer deserializer(root);
    TraceNode node(deserializer.deserialize());
    EXPECT_FALSE(node.hasNote());
    EXPECT_EQUAL(zero_system_time, node.getTimestamp());
}


TEST("that a single trace node can be deserialized") {
    Slime slime;
    Cursor & root(slime.setObject());
    root.setLong("timestamp", 1234);
    root.setString("payload", "hello");
    SlimeTraceDeserializer deserializer(root);
    TraceNode node(deserializer.deserialize());
    EXPECT_EQUAL(as_ms(1234), node.getTimestamp());
    EXPECT_TRUE(node.hasNote());
    EXPECT_EQUAL("hello", node.getNote());
}

TEST("that a trace node with children can be deserialized") {
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
    ASSERT_EQUAL(2u, node.getNumChildren());
    TraceNode fooNode(node.getChild(0));
    ASSERT_EQUAL(1u, fooNode.getNumChildren());
    TraceNode fooBarNode(fooNode.getChild(0));
    EXPECT_EQUAL("world", fooBarNode.getNote());
    TraceNode barNode(node.getChild(1));
    EXPECT_EQUAL("!", barNode.getNote());
    ASSERT_EQUAL(0u, barNode.getNumChildren());
}

TEST("test serialization and deserialization") {
    TraceNode root;
    root.addChild("foo", as_ms(45));
    root.addChild("bar");
    root.addChild(TraceNode());
    Slime slime;
    SlimeTraceSerializer s(slime.setObject());
    root.accept(s);
    SlimeTraceDeserializer d(slime.get());
    TraceNode root2(d.deserialize());
    ASSERT_EQUAL(3u, root2.getNumChildren());
}

TEST_MAIN() { TEST_RUN_ALL(); }
