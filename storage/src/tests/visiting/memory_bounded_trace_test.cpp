// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/visiting/memory_bounded_trace.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage {

constexpr vespalib::system_time epoch;

TEST(MemoryBoundedTraceTest, no_memory_reported_used_when_empty) {
    MemoryBoundedTrace trace(100);
    EXPECT_EQ(0, trace.getApproxMemoryUsed());
}

TEST(MemoryBoundedTraceTest, memory_used_is_string_length_for_leaf_node) {
    MemoryBoundedTrace trace(100);
    EXPECT_TRUE(trace.add(mbus::TraceNode("hello world", epoch)));
    EXPECT_EQ(11, trace.getApproxMemoryUsed());
}

TEST(MemoryBoundedTraceTest, memory_used_is_accumulated_recursively_for_non_leaf_nodes) {
    MemoryBoundedTrace trace(100);
    mbus::TraceNode innerNode;
    innerNode.addChild("hello world");
    innerNode.addChild("goodbye moon");
    EXPECT_TRUE(trace.add(innerNode));
    EXPECT_EQ(23, trace.getApproxMemoryUsed());
}

TEST(MemoryBoundedTraceTest, trace_nodes_can_be_moved_and_implicitly_cleared) {
    MemoryBoundedTrace trace(100);
    EXPECT_TRUE(trace.add(mbus::TraceNode("hello world", epoch)));
    mbus::Trace target;
    trace.moveTraceTo(target);
    EXPECT_EQ(1, target.getNumChildren());
    EXPECT_EQ(0, trace.getApproxMemoryUsed());
    
    mbus::Trace emptinessCheck;
    trace.moveTraceTo(emptinessCheck);
    EXPECT_EQ(0, emptinessCheck.getNumChildren());
}

/**
 * We want trace subtrees to be strictly ordered so that the message about
 * omitted traces will remain soundly as the last ordered node. There is no
 * particular performance reason for not having strict mode enabled to the
 * best of my knowledge, since the internal backing data structure is an
 * ordered vector anyhow.
 */
TEST(MemoryBoundedTraceTest, moved_trace_tree_is_marked_as_strict) {
    MemoryBoundedTrace trace(100);
    EXPECT_TRUE(trace.add(mbus::TraceNode("hello world", epoch)));
    mbus::Trace target;
    trace.moveTraceTo(target);
    EXPECT_EQ(1, target.getNumChildren());
    EXPECT_TRUE(target.getChild(0).isStrict());
}

TEST(MemoryBoundedTraceTest, can_not_add_more_nodes_when_memory_used_exceeds_upper_bound) {
    // Note: we allow one complete node tree to exceed the bounds, but as soon
    // as the bound is exceeded no further nodes can be added.
    MemoryBoundedTrace trace(10);
    EXPECT_TRUE(trace.add(mbus::TraceNode("hello world", epoch)));
    EXPECT_EQ(11, trace.getApproxMemoryUsed());

    EXPECT_FALSE(trace.add(mbus::TraceNode("the quick red fox runs across "
                                           "the freeway", epoch)));
    EXPECT_EQ(11, trace.getApproxMemoryUsed());

    mbus::Trace target;
    trace.moveTraceTo(target);
    // Twice nested node (root -> added trace tree -> leaf with txt).
    EXPECT_EQ(1, target.getNumChildren());
    EXPECT_GE(target.getChild(0).getNumChildren(), 1);
    EXPECT_EQ("hello world", target.getChild(0).getChild(0).getNote());
}

TEST(MemoryBoundedTraceTest, moved_tree_includes_stats_node_when_nodes_omitted) {
    MemoryBoundedTrace trace(5);
    EXPECT_TRUE(trace.add(mbus::TraceNode("abcdef", epoch)));
    EXPECT_FALSE(trace.add(mbus::TraceNode("ghijkjlmn", epoch)));

    mbus::Trace target;
    trace.moveTraceTo(target);
    EXPECT_EQ(1, target.getNumChildren());
    EXPECT_EQ(2, target.getChild(0).getNumChildren());
    vespalib::string expected("Trace too large; omitted 1 subsequent trace "
                              "trees containing a total of 9 bytes");
    EXPECT_EQ(expected, target.getChild(0).getChild(1).getNote());
}

} // storage
