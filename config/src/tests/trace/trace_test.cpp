// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/trace.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>


using namespace config;
using namespace vespalib;
using namespace vespalib::slime;

struct FixedClock : public Clock
{
    FixedClock() : _currentTime() { }
    ~FixedClock() override;
    vespalib::system_time _currentTime;
    vespalib::system_time currentTime() const override { return _currentTime; }
};

FixedClock::~FixedClock() = default;

TEST(TraceTest, that_trace_can_be_serialized_and_deserialized)
{
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

    EXPECT_EQ(trace.toString(), trace3.toString());
}

TEST(TraceTest, that_trace_level_is_taken_into_account)
{
    FixedClock f1;
    f1._currentTime = vespalib::system_time(3ms);
    Trace trace(4, f1);
    trace.trace(4, "foo");
    trace.trace(5, "bar");
    EXPECT_EQ("[\n"
"    {\n"
"        \"timestamp\": 3,\n"
"        \"payload\": \"foo\"\n"
"    }\n"
"]\n", trace.toString());
}

TEST(TraceTest, that_trace_can_be_copied)
{
    Trace trace(3);
    trace.trace(2, "foo");
    trace.trace(3, "bar");
    Trace trace2(trace);
    EXPECT_EQ(trace.toString(), trace2.toString());
}

constexpr vespalib::system_time epoch;

TEST(TraceTest, ensure_that_system_clock_is_used_by_default)
{
    Trace trace(2);
    trace.trace(1, "foo");
    TraceNode child(trace.getRoot().getChild(0));
    EXPECT_TRUE(child.getTimestamp() > epoch);
}

GTEST_MAIN_RUN_ALL_TESTS()
