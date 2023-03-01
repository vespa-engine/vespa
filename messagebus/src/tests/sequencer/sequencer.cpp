// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/sequencer.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP("sequencer_test");

using namespace mbus;

// --------------------------------------------------------------------------------
//
// Setup.
//
// --------------------------------------------------------------------------------

struct MyQueue : public RoutableQueue {

    virtual ~MyQueue() {
        while (size() > 0) {
            Routable::UP obj = dequeue();
            obj->getCallStack().discard();
        }
    }

    bool checkReply(bool hasSeqId, uint64_t seqId) {
        if (size() == 0) {
            LOG(error, "checkReply(): No reply in queue.");
            return false;
        }
        Routable::UP obj = dequeue();
        if (!obj->isReply()) {
            LOG(error, "checkReply(): Got message when expecting reply.");
            return false;
        }
        Reply::UP reply(static_cast<Reply*>(obj.release()));
        Message::UP msg = reply->getMessage();
        if ( ! msg) {
            LOG(error, "checkReply(): Reply has no message attached.");
            return false;
        }
        if (hasSeqId) {
            if (!msg->hasSequenceId()) {
                LOG(error, "checkReply(): Expected sequence id %" PRIu64 ", got none.",
                    seqId);
                return false;
            }
            if (msg->getSequenceId() != seqId) {
                LOG(error, "checkReply(): Expected sequence id %" PRIu64 ", got %" PRIu64 ".",
                    seqId, msg->getSequenceId());
                return false;
            }
        } else {
            if (msg->hasSequenceId()) {
                LOG(error, "checkReply(): Message has unexpected sequence id %" PRIu64 ".",
                    msg->getSequenceId());
                return false;
            }
        }
        return true;
    }

    void replyNext() {
        Routable::UP obj = dequeue();
        Message::UP msg(static_cast<Message*>(obj.release()));

        Reply::UP reply(new EmptyReply());
        reply->swapState(*msg);
        reply->setMessage(std::move(msg));
        IReplyHandler &handler = reply->getCallStack().pop(*reply);
        handler.handleReply(std::move(reply));
    }

    Message::UP createMessage(bool hasSeqId, uint64_t seqId) {
        Message::UP ret(new SimpleMessage("foo", hasSeqId, seqId));
        ret->pushHandler(*this);
        return ret;
    }
};

class Test : public vespalib::TestApp {
private:
    void testSyncNone();
    void testSyncId();

public:
    int Main() override {
        TEST_INIT("sequencer_test");

        testSyncNone(); TEST_FLUSH();
        testSyncId();   TEST_FLUSH();

        TEST_DONE();
    }
};

TEST_APPHOOK(Test);

// --------------------------------------------------------------------------------
//
// Tests.
//
// --------------------------------------------------------------------------------

void
Test::testSyncNone()
{
    MyQueue       src;
    MyQueue       dst;
    Sequencer     seq(dst);

    seq.handleMessage(src.createMessage(false, 0));
    seq.handleMessage(src.createMessage(false, 0));
    seq.handleMessage(src.createMessage(false, 0));
    seq.handleMessage(src.createMessage(false, 0));
    seq.handleMessage(src.createMessage(false, 0));
    EXPECT_EQUAL(0u, src.size());
    EXPECT_EQUAL(5u, dst.size());

    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    EXPECT_EQUAL(5u, src.size());
    EXPECT_EQUAL(0u, dst.size());

    EXPECT_TRUE(src.checkReply(false, 0));
    EXPECT_TRUE(src.checkReply(false, 0));
    EXPECT_TRUE(src.checkReply(false, 0));
    EXPECT_TRUE(src.checkReply(false, 0));
    EXPECT_TRUE(src.checkReply(false, 0));
    EXPECT_EQUAL(0u, src.size());
    EXPECT_EQUAL(0u, dst.size());
}

void
Test::testSyncId()
{
    MyQueue     src;
    MyQueue     dst;
    Sequencer   seq(dst);

    seq.handleMessage(src.createMessage(true, 1));
    seq.handleMessage(src.createMessage(true, 2));
    seq.handleMessage(src.createMessage(true, 3));
    seq.handleMessage(src.createMessage(true, 4));
    seq.handleMessage(src.createMessage(true, 5));
    EXPECT_EQUAL(0u, src.size());
    EXPECT_EQUAL(5u, dst.size());

    seq.handleMessage(src.createMessage(true, 1));
    seq.handleMessage(src.createMessage(true, 5));
    seq.handleMessage(src.createMessage(true, 2));
    seq.handleMessage(src.createMessage(true, 10));
    seq.handleMessage(src.createMessage(true, 4));
    seq.handleMessage(src.createMessage(true, 3));
    EXPECT_EQUAL(0u, src.size());
    EXPECT_EQUAL(6u, dst.size());

    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    EXPECT_EQUAL(5u, src.size());
    EXPECT_EQUAL(6u, dst.size());

    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    dst.replyNext();
    EXPECT_EQUAL(11u, src.size());
    EXPECT_EQUAL(0u, dst.size());

    EXPECT_TRUE(src.checkReply(true, 1));
    EXPECT_TRUE(src.checkReply(true, 2));
    EXPECT_TRUE(src.checkReply(true, 3));
    EXPECT_TRUE(src.checkReply(true, 4));
    EXPECT_TRUE(src.checkReply(true, 5));
    EXPECT_TRUE(src.checkReply(true, 10));
    EXPECT_TRUE(src.checkReply(true, 1));
    EXPECT_TRUE(src.checkReply(true, 2));
    EXPECT_TRUE(src.checkReply(true, 3));
    EXPECT_TRUE(src.checkReply(true, 4));
    EXPECT_TRUE(src.checkReply(true, 5));
    EXPECT_EQUAL(0u, src.size());
    EXPECT_EQUAL(0u, dst.size());
}
