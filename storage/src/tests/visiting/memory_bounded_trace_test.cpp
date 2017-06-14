// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/visiting/memory_bounded_trace.h>

namespace storage {

class MemoryBoundedTraceTest : public CppUnit::TestFixture
{
    CPPUNIT_TEST_SUITE(MemoryBoundedTraceTest);
    CPPUNIT_TEST(noMemoryReportedUsedWhenEmpty);
    CPPUNIT_TEST(memoryUsedIsStringLengthForLeafNode);
    CPPUNIT_TEST(memoryUsedIsAccumulatedRecursivelyForNonLeafNodes);
    CPPUNIT_TEST(traceNodesCanBeMovedAndImplicitlyCleared);
    CPPUNIT_TEST(movedTraceTreeIsMarkedAsStrict);
    CPPUNIT_TEST(canNotAddMoreNodesWhenMemoryUsedExceedsUpperBound);
    CPPUNIT_TEST(movedTreeIncludesStatsNodeWhenNodesOmitted);
    CPPUNIT_TEST_SUITE_END();

public:
    void noMemoryReportedUsedWhenEmpty();
    void memoryUsedIsStringLengthForLeafNode();
    void memoryUsedIsAccumulatedRecursivelyForNonLeafNodes();
    void traceNodesCanBeMovedAndImplicitlyCleared();
    void movedTraceTreeIsMarkedAsStrict();
    void canNotAddMoreNodesWhenMemoryUsedExceedsUpperBound();
    void movedTreeIncludesStatsNodeWhenNodesOmitted();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemoryBoundedTraceTest);

void
MemoryBoundedTraceTest::noMemoryReportedUsedWhenEmpty()
{
    MemoryBoundedTrace trace(100);
    CPPUNIT_ASSERT_EQUAL(size_t(0), trace.getApproxMemoryUsed());
}

void
MemoryBoundedTraceTest::memoryUsedIsStringLengthForLeafNode()
{
    MemoryBoundedTrace trace(100);
    CPPUNIT_ASSERT(trace.add(mbus::TraceNode("hello world", 0)));
    CPPUNIT_ASSERT_EQUAL(size_t(11), trace.getApproxMemoryUsed());
}

void
MemoryBoundedTraceTest::memoryUsedIsAccumulatedRecursivelyForNonLeafNodes()
{
    MemoryBoundedTrace trace(100);
    mbus::TraceNode innerNode;
    innerNode.addChild("hello world");
    innerNode.addChild("goodbye moon");
    CPPUNIT_ASSERT(trace.add(innerNode));
    CPPUNIT_ASSERT_EQUAL(size_t(23), trace.getApproxMemoryUsed());
}

void
MemoryBoundedTraceTest::traceNodesCanBeMovedAndImplicitlyCleared()
{
    MemoryBoundedTrace trace(100);
    CPPUNIT_ASSERT(trace.add(mbus::TraceNode("hello world", 0)));
    mbus::TraceNode target;
    trace.moveTraceTo(target);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), target.getNumChildren());
    CPPUNIT_ASSERT_EQUAL(size_t(0), trace.getApproxMemoryUsed());
    
    mbus::TraceNode emptinessCheck;
    trace.moveTraceTo(emptinessCheck);
    CPPUNIT_ASSERT_EQUAL(uint32_t(0), emptinessCheck.getNumChildren());
}

/**
 * We want trace subtrees to be strictly ordered so that the message about
 * omitted traces will remain soundly as the last ordered node. There is no
 * particular performance reason for not having strict mode enabled to the
 * best of my knowledge, since the internal backing data structure is an
 * ordered vector anyhow.
 */
void
MemoryBoundedTraceTest::movedTraceTreeIsMarkedAsStrict()
{
    MemoryBoundedTrace trace(100);
    CPPUNIT_ASSERT(trace.add(mbus::TraceNode("hello world", 0)));
    mbus::TraceNode target;
    trace.moveTraceTo(target);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), target.getNumChildren());
    CPPUNIT_ASSERT(target.getChild(0).isStrict());
}

void
MemoryBoundedTraceTest::canNotAddMoreNodesWhenMemoryUsedExceedsUpperBound()
{
    // Note: we allow one complete node tree to exceed the bounds, but as soon
    // as the bound is exceeded no further nodes can be added.
    MemoryBoundedTrace trace(10);
    CPPUNIT_ASSERT(trace.add(mbus::TraceNode("hello world", 0)));
    CPPUNIT_ASSERT_EQUAL(size_t(11), trace.getApproxMemoryUsed());

    CPPUNIT_ASSERT(!trace.add(mbus::TraceNode("the quick red fox runs across "
                                              "the freeway", 0)));
    CPPUNIT_ASSERT_EQUAL(size_t(11), trace.getApproxMemoryUsed());

    mbus::TraceNode target;
    trace.moveTraceTo(target);
    // Twice nested node (root -> added trace tree -> leaf with txt).
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), target.getNumChildren());
    CPPUNIT_ASSERT(target.getChild(0).getNumChildren() >= 1);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("hello world"),
                         target.getChild(0).getChild(0).getNote());
}

void
MemoryBoundedTraceTest::movedTreeIncludesStatsNodeWhenNodesOmitted()
{
    MemoryBoundedTrace trace(5);
    CPPUNIT_ASSERT(trace.add(mbus::TraceNode("abcdef", 0)));
    CPPUNIT_ASSERT(!trace.add(mbus::TraceNode("ghijkjlmn", 0)));

    mbus::TraceNode target;
    trace.moveTraceTo(target);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), target.getNumChildren());
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), target.getChild(0).getNumChildren());
    vespalib::string expected("Trace too large; omitted 1 subsequent trace "
                              "trees containing a total of 9 bytes");
    CPPUNIT_ASSERT_EQUAL(expected, target.getChild(0).getChild(1).getNote());
}

} // storage

