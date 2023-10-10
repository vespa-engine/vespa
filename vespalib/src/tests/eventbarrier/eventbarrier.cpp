// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/eventbarrier.hpp>

using namespace vespalib;

struct MyBarrier {
    bool done;
    MyBarrier() : done(false) {}
    void completeBarrier() {
        done = true;
    }
};

class Test : public TestApp
{
public:
    void testEmpty();
    void testSimple();
    void testBarrierChain();
    void testEventAfter();
    void testReorder();
    int Main() override;
};

void
Test::testEmpty()
{
    // waiting for an empty set of events

    MyBarrier b;
    EventBarrier<MyBarrier> eb;

    EXPECT_TRUE(!eb.startBarrier(b));
    EXPECT_TRUE(!b.done);
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    uint32_t token = eb.startEvent();
    eb.completeEvent(token);

    EXPECT_TRUE(!eb.startBarrier(b));
    EXPECT_TRUE(!b.done);
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);
}

void
Test::testSimple()
{
    // a single barrier waiting for a single event

    MyBarrier b;
    EventBarrier<MyBarrier> eb;
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    uint32_t token = eb.startEvent();
    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    EXPECT_TRUE(eb.startBarrier(b));
    EXPECT_TRUE(!b.done);
    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 1u);

    eb.completeEvent(token);
    EXPECT_TRUE(b.done);
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);
}

void
Test::testBarrierChain()
{
    // more than one barrier waiting for the same set of events

    MyBarrier b1;
    MyBarrier b2;
    MyBarrier b3;
    EventBarrier<MyBarrier> eb;
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    uint32_t token = eb.startEvent();
    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    EXPECT_TRUE(eb.startBarrier(b1));
    EXPECT_TRUE(eb.startBarrier(b2));
    EXPECT_TRUE(eb.startBarrier(b3));
    EXPECT_TRUE(!b1.done);
    EXPECT_TRUE(!b2.done);
    EXPECT_TRUE(!b3.done);

    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 3u);

    eb.completeEvent(token);
    EXPECT_TRUE(b1.done);
    EXPECT_TRUE(b2.done);
    EXPECT_TRUE(b3.done);
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);
}

void
Test::testEventAfter()
{
    // new events starting after the start of a barrier

    MyBarrier b;
    EventBarrier<MyBarrier> eb;
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    uint32_t token = eb.startEvent();
    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    EXPECT_TRUE(eb.startBarrier(b));
    EXPECT_TRUE(!b.done);
    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 1u);

    uint32_t t2 = eb.startEvent();
    EXPECT_TRUE(!b.done);
    EXPECT_EQUAL(eb.countEvents(), 2u);
    EXPECT_EQUAL(eb.countBarriers(), 1u);

    eb.completeEvent(token);
    EXPECT_TRUE(b.done);
    EXPECT_EQUAL(eb.countEvents(), 1u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);

    eb.completeEvent(t2);
    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);
}

void
Test::testReorder()
{
    // events completing in a different order than they started

    MyBarrier b1;
    MyBarrier b2;
    MyBarrier b3;
    EventBarrier<MyBarrier> eb;

    uint32_t t1 = eb.startEvent();
    eb.startBarrier(b1);
    uint32_t t2 = eb.startEvent();
    eb.startBarrier(b2);
    uint32_t t3 = eb.startEvent();
    eb.startBarrier(b3);
    uint32_t t4 = eb.startEvent();

    EXPECT_EQUAL(eb.countEvents(), 4u);
    EXPECT_EQUAL(eb.countBarriers(), 3u);

    EXPECT_TRUE(!b1.done);
    EXPECT_TRUE(!b2.done);
    EXPECT_TRUE(!b3.done);

    eb.completeEvent(t4);
    EXPECT_TRUE(!b1.done);
    EXPECT_TRUE(!b2.done);
    EXPECT_TRUE(!b3.done);

    eb.completeEvent(t3);
    EXPECT_TRUE(!b1.done);
    EXPECT_TRUE(!b2.done);
    EXPECT_TRUE(!b3.done);

    eb.completeEvent(t1);
    EXPECT_TRUE(b1.done);
    EXPECT_TRUE(!b2.done);
    EXPECT_TRUE(!b3.done);

    eb.completeEvent(t2);
    EXPECT_TRUE(b1.done);
    EXPECT_TRUE(b2.done);
    EXPECT_TRUE(b3.done);

    EXPECT_EQUAL(eb.countEvents(), 0u);
    EXPECT_EQUAL(eb.countBarriers(), 0u);
}

int
Test::Main()
{
    TEST_INIT("eventbarrier_test");
    testEmpty();
    testSimple();
    testBarrierChain();
    testEventAfter();
    testReorder();
    TEST_DONE();
}

TEST_APPHOOK(Test);
