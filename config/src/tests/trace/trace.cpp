// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/trace.h>
#include <vespa/vespalib/trace/tracenode.h>


using namespace config;
using namespace vespalib;
using namespace vespalib::slime;

struct FixedClock : public Clock
{
    FixedClock() : currentTime(0) { }
    int64_t currentTime;
    int64_t currentTimeMillis() const override { return currentTime; }
};

TEST("that trace can be serialized and deserialized") {
    Trace trace(4);
    trace.trace(4, "foo");
    trace.trace(3, "bar");
    trace.trace(5, "baz");

    Slime slime;
    Cursor & cursor(slime.setObject());
    trace.serialize(cursor);

    Trace trace2;
    trace2.deserialize(slime.get());

    Slime slime2;
    trace2.serialize(slime2.setObject());
    Trace trace3;
    trace3.deserialize(slime2.get());

    EXPECT_EQUAL(trace.toString(), trace3.toString());
}

TEST_F("that trace level is taken into account", FixedClock) {
    f1.currentTime = 3;
    Trace trace(4, f1);
    trace.trace(4, "foo");
    trace.trace(5, "bar");
    EXPECT_EQUAL("[\n"
"    {\n"
"        \"timestamp\": 3,\n"
"        \"payload\": \"foo\"\n"
"    }\n"
"]\n", trace.toString());
}

TEST("that trace can be copied") {
    Trace trace(3);
    trace.trace(2, "foo");
    trace.trace(3, "bar");
    Trace trace2(trace);
    EXPECT_EQUAL(trace.toString(), trace2.toString());
}

TEST("ensure that system clock is used by default") {
    Trace trace(2);
    trace.trace(1, "foo");
    TraceNode child(trace.getRoot().getChild(0));
    EXPECT_TRUE(child.getTimestamp() > 0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
