// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("messageordering_test");

using namespace mbus;
using namespace std::chrono_literals;

TEST_SETUP(Test);

RoutingSpec
getRouting()
{
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("dst", "test/dst/session"))
                  .addRoute(RouteSpec("test").addHop("dst")));
}

class MultiReceptor : public IMessageHandler
{
private:
    std::mutex _mon;
    DestinationSession* _destinationSession;
    int _messageCounter;

public:
    MultiReceptor()
        : _mon(),
          _destinationSession(nullptr),
          _messageCounter(0)
    {}
    void handleMessage(Message::UP msg) override
     {
        auto & simpleMsg(dynamic_cast<SimpleMessage&>(*msg));
        LOG(spam, "Attempting to acquire lock for %s",
            simpleMsg.getValue().c_str());

        std::lock_guard guard(_mon);

        vespalib::string expected(vespalib::make_string("%d", _messageCounter));
        LOG(debug, "Got message %p with %s, expecting %s",
            msg.get(),
            simpleMsg.getValue().c_str(),
            expected.c_str());

        auto sr =std::make_unique<SimpleReply>("test reply");
        msg->swapState(*sr);

        if (simpleMsg.getValue() != expected) {
            std::stringstream ss;
            ss << "Received out-of-sequence message! Expected "
               << expected
               << ", but got "
               << simpleMsg.getValue();
            //LOG(warning, "%s", ss.str().c_str());
            sr->addError(Error(ErrorCode::FATAL_ERROR, ss.str()));
        }
        sr->setValue(simpleMsg.getValue());

        ++_messageCounter;
        _destinationSession->reply(Reply::UP(sr.release()));
    }
    void setDestinationSession(DestinationSession& sess) {
        _destinationSession = &sess;
    }
};

class VerifyReplyReceptor : public IReplyHandler
{
    mutable std::mutex              _mon;
    mutable std::condition_variable _cond;
    std::string _failure;
    int _replyCount;
public:
    ~VerifyReplyReceptor() override;
    VerifyReplyReceptor();
    void handleReply(Reply::UP reply) override;
    void waitUntilDone(int waitForCount) const;
    const std::string& getFailure() const { return _failure; }
};

VerifyReplyReceptor::~VerifyReplyReceptor() = default;
VerifyReplyReceptor::VerifyReplyReceptor()
    : _mon(),
      _cond(),
      _failure(),
      _replyCount(0)
{}

void
VerifyReplyReceptor::handleReply(Reply::UP reply)
{
    std::lock_guard lock(_mon);
    if (reply->hasErrors()) {
        std::ostringstream ss;
        ss << "Reply failed with "
           << reply->getError(0).getMessage()
           << "\n"
           << reply->getTrace().toString();
        if (_failure.empty()) {
            _failure = ss.str();
        }
        LOG(warning, "%s", ss.str().c_str());
    } else {
        vespalib::string expected(vespalib::make_string("%d", _replyCount));
        auto & simpleReply(static_cast<SimpleReply&>(*reply));
        if (simpleReply.getValue() != expected) {
            std::stringstream ss;
            ss << "Received out-of-sequence reply! Expected "
               << expected
               << ", but got "
               << simpleReply.getValue();
            LOG(warning, "%s", ss.str().c_str());
            if (_failure.empty()) {
                _failure = ss.str();
            }
        }
    }
    ++_replyCount;
    _cond.notify_all();
}
void
VerifyReplyReceptor::waitUntilDone(int waitForCount) const
{
    std::unique_lock guard(_mon);
    while (_replyCount < waitForCount) {
        _cond.wait_for(guard, 1s);
    }
}

int
Test::Main()
{
    TEST_INIT("messageordering_test");

    Slobrok     slobrok;
    TestServer  srcNet(Identity("test/src"), getRouting(), slobrok);
    TestServer  dstNet(Identity("test/dst"), getRouting(), slobrok);

    VerifyReplyReceptor src;
    MultiReceptor dst;

    SourceSessionParams ssp;
    ssp.setThrottlePolicy(IThrottlePolicy::SP());
    ssp.setTimeout(400s);
    SourceSession::UP      ss = srcNet.mb.createSourceSession(src, ssp);
    DestinationSession::UP ds = dstNet.mb.createDestinationSession("session", true, dst);
    dst.setDestinationSession(*ds);
    ASSERT_EQUAL(400s, ssp.getTimeout());

    // wait for slobrok registration
    ASSERT_TRUE(srcNet.waitSlobrok("test/dst/session"));

    // same message id for all messages in order to guarantee ordering
    int commonMessageId = 42;

    // send messages on client
    const int messageCount = 5000;
    for (int i = 0; i < messageCount; ++i) {
        vespalib::string str(vespalib::make_string("%d", i));
        //std::this_thread::sleep_for(1ms);
        auto msg = std::make_unique<SimpleMessage>(str, true, commonMessageId);
        msg->getTrace().setLevel(9);
        //LOG(debug, "Sending message %p for %d", msg.get(), i);
        ASSERT_EQUAL(uint32_t(ErrorCode::NONE),
                     ss->send(std::move(msg), "test").getError().getCode());
    }
    src.waitUntilDone(messageCount);

    ASSERT_EQUAL(std::string(), src.getFailure());

    TEST_DONE();
}
