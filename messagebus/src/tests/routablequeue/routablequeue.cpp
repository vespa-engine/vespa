// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

class TestMessage : public SimpleMessage {
private:
    uint32_t _id;
    static uint32_t _cnt;
public:
    TestMessage(uint32_t id) : SimpleMessage(""), _id(id) { ++_cnt; }
    ~TestMessage() { --_cnt; }
    uint32_t getType() const override { return _id; }
    static uint32_t getCnt() { return _cnt; }
};
uint32_t TestMessage::_cnt = 0;

class TestReply : public SimpleReply {
private:
    uint32_t _id;
    static uint32_t _cnt;
public:
    TestReply(uint32_t id) : SimpleReply(""), _id(id) { ++_cnt; }
    ~TestReply() { --_cnt; }
    uint32_t getType() const override { return _id; }
    static uint32_t getCnt() { return _cnt; }
};
uint32_t TestReply::_cnt = 0;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("routablequeue_test");
    {
        RoutableQueue rq;
        EXPECT_TRUE(rq.size() == 0);
        EXPECT_TRUE(rq.dequeue().get() == 0);
        EXPECT_TRUE(rq.dequeue(100ms).get() == 0);
        EXPECT_TRUE(TestMessage::getCnt() == 0);
        EXPECT_TRUE(TestReply::getCnt() == 0);
        rq.enqueue(Routable::UP(new TestMessage(101)));
        EXPECT_TRUE(rq.size() == 1);
        EXPECT_TRUE(TestMessage::getCnt() == 1);
        EXPECT_TRUE(TestReply::getCnt() == 0);
        rq.enqueue(Routable::UP(new TestReply(201)));
        EXPECT_TRUE(rq.size() == 2);
        EXPECT_TRUE(TestMessage::getCnt() == 1);
        EXPECT_TRUE(TestReply::getCnt() == 1);
        rq.handleMessage(Message::UP(new TestMessage(102)));
        EXPECT_TRUE(rq.size() == 3);
        EXPECT_TRUE(TestMessage::getCnt() == 2);
        EXPECT_TRUE(TestReply::getCnt() == 1);
        rq.handleReply(Reply::UP(new TestReply(202)));
        EXPECT_TRUE(rq.size() == 4);
        EXPECT_TRUE(TestMessage::getCnt() == 2);
        EXPECT_TRUE(TestReply::getCnt() == 2);
        {
            Routable::UP r = rq.dequeue();
            ASSERT_TRUE(r);
            EXPECT_TRUE(rq.size() == 3);
            EXPECT_TRUE(r->getType() == 101);
        }
        EXPECT_TRUE(TestMessage::getCnt() == 1);
        EXPECT_TRUE(TestReply::getCnt() == 2);
        {
            Routable::UP r = rq.dequeue();
            ASSERT_TRUE(r);
            EXPECT_TRUE(rq.size() == 2);
            EXPECT_TRUE(r->getType() == 201);
        }
        EXPECT_TRUE(TestMessage::getCnt() == 1);
        EXPECT_TRUE(TestReply::getCnt() == 1);
        rq.handleMessage(Message::UP(new TestMessage(103)));
        EXPECT_TRUE(rq.size() == 3);
        EXPECT_TRUE(TestMessage::getCnt() == 2);
        EXPECT_TRUE(TestReply::getCnt() == 1);
        rq.handleReply(Reply::UP(new TestReply(203)));
        EXPECT_TRUE(rq.size() == 4);
        EXPECT_TRUE(TestMessage::getCnt() == 2);
        EXPECT_TRUE(TestReply::getCnt() == 2);
        {
            Routable::UP r = rq.dequeue();
            ASSERT_TRUE(r);
            EXPECT_TRUE(rq.size() == 3);
            EXPECT_TRUE(r->getType() == 102);
        }
        EXPECT_TRUE(TestMessage::getCnt() == 1);
        EXPECT_TRUE(TestReply::getCnt() == 2);
        {
            Routable::UP r = rq.dequeue();
            ASSERT_TRUE(r);
            EXPECT_TRUE(rq.size() == 2);
            EXPECT_TRUE(r->getType() == 202);
        }
        EXPECT_TRUE(TestMessage::getCnt() == 1);
        EXPECT_TRUE(TestReply::getCnt() == 1);
    }
    EXPECT_TRUE(TestMessage::getCnt() == 0);
    EXPECT_TRUE(TestReply::getCnt() == 0);
    TEST_DONE();
}
