// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/imessagehandler.h>
#include <vespa/messagebus/replygate.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

struct MyGate : public ReplyGate
{
    static int ctorCnt;
    static int dtorCnt;
    MyGate(IMessageHandler &sender) : ReplyGate(sender) {
        ++ctorCnt;
    }
    virtual ~MyGate() {
        ++dtorCnt;
    }
};
int MyGate::ctorCnt = 0;
int MyGate::dtorCnt = 0;

struct MyReply : public EmptyReply
{
    static int ctorCnt;
    static int dtorCnt;
    MyReply() : EmptyReply() {
        ++ctorCnt;
    }
    virtual ~MyReply() {
        ++dtorCnt;
    }
};
int MyReply::ctorCnt = 0;
int MyReply::dtorCnt = 0;

struct MySender : public IMessageHandler
{
    // giving a sync reply here is against the API contract, but it is
    // ok for testing.
    void handleMessage(Message::UP msg) override {
        Reply::UP reply(new MyReply());
        msg->swapState(*reply);
        IReplyHandler &handler = reply->getCallStack().pop(*reply);
        handler.handleReply(std::move(reply));
    }
};

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("replygate_test");
    {
        RoutableQueue q;
        MySender      sender;
        MyGate       *gate = new MyGate(sender);
        {
            Message::UP msg(new SimpleMessage("test"));
            msg->pushHandler(q);
            gate->handleMessage(std::move(msg));
        }
        EXPECT_TRUE(q.size() == 1);
        EXPECT_TRUE(MyReply::ctorCnt == 1);
        EXPECT_TRUE(MyReply::dtorCnt == 0);
        gate->close();
        {
            Message::UP msg(new SimpleMessage("test"));
            msg->pushHandler(q);
            gate->handleMessage(std::move(msg));
        }
        EXPECT_TRUE(q.size() == 1);
        EXPECT_TRUE(MyReply::ctorCnt == 2);
        EXPECT_TRUE(MyReply::dtorCnt == 1);
        EXPECT_TRUE(MyGate::ctorCnt == 1);
        EXPECT_TRUE(MyGate::dtorCnt == 0);
        gate->subRef();
        EXPECT_TRUE(MyGate::ctorCnt == 1);
        EXPECT_TRUE(MyGate::dtorCnt == 1);
    }
    EXPECT_TRUE(MyReply::ctorCnt == 2);
    EXPECT_TRUE(MyReply::dtorCnt == 2);
    TEST_DONE();
}
