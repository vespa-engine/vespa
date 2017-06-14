// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/intermediatesession.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/testlib/oosserver.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

struct Handler : public IMessageHandler
{
    DestinationSession::UP session;
    Handler(MessageBus &mb) : session() {
        session = mb.createDestinationSession("session", true, *this);
    }
    ~Handler() {
        session.reset();
    }
    void handleMessage(Message::UP msg) override {
        session->acknowledge(std::move(msg));
    }
};


class Test : public vespalib::TestApp {
private:
    SourceSession::UP _session;
    RoutableQueue     _handler;

    bool checkError(const string &dst, uint32_t error);

public:
    Test();
    ~Test();
    int Main() override;
};

TEST_APPHOOK(Test);

Test::Test() :
    _session(),
    _handler()
{}

Test::~Test() {}
bool
Test::checkError(const string &dst, uint32_t error)
{
    if (!EXPECT_TRUE(_session.get() != NULL)) {
        return false;
    }
    Message::UP msg(new SimpleMessage("msg"));
    msg->getTrace().setLevel(9);
    if (!EXPECT_TRUE(_session->send(std::move(msg), Route::parse(dst)).isAccepted())) {
        return false;
    }
    Routable::UP reply = _handler.dequeue(10000);
    if (!EXPECT_TRUE(reply.get() != NULL)) {
        return false;
    }
    if (!EXPECT_TRUE(reply->isReply())) {
        return false;
    }
    Reply &ref = static_cast<Reply&>(*reply);
    printf("%s", ref.getTrace().toString().c_str());
    if (error == ErrorCode::NONE) {
        if (!EXPECT_TRUE(!ref.hasErrors())) {
            return false;
        }
    } else {
        if (!EXPECT_TRUE(ref.hasErrors())) {
            return false;
        }
        if (!EXPECT_EQUAL(error, ref.getError(0).getCode())) {
            return false;
        }
    }
    return true;
}

int
Test::Main()
{
    TEST_INIT("oos_test");

    Slobrok    slobrok;
    TestServer src(Identity(""), RoutingSpec(), slobrok, "oos/*");
    TestServer dst1(Identity("dst1"), RoutingSpec(), slobrok);
    TestServer dst2(Identity("dst2"), RoutingSpec(), slobrok);
    TestServer dst3(Identity("dst3"), RoutingSpec(), slobrok);
    TestServer dst4(Identity("dst4"), RoutingSpec(), slobrok);
    TestServer dst5(Identity("dst5"), RoutingSpec(), slobrok);
    Handler h1(dst1.mb);
    Handler h2(dst2.mb);
    Handler h3(dst3.mb);
    Handler h4(dst4.mb);
    Handler h5(dst5.mb);
    EXPECT_TRUE(src.waitSlobrok("*/session", 5));

    _session = src.mb.createSourceSession(_handler);
    EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst2/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst3/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst4/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst5/session", ErrorCode::NONE));
    TEST_FLUSH();
    OOSServer oosServer(slobrok, "oos/1", OOSState()
                        .add("dst2/session")
                        .add("dst3/session"));
    EXPECT_TRUE(src.waitSlobrok("oos/*", 1));
    EXPECT_TRUE(src.waitState(OOSState()
                             .add("dst2/session")
                             .add("dst3/session")));
    EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE)); // test 9
    EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS)); // return without reply?!?
    EXPECT_TRUE(checkError("dst3/session", ErrorCode::SERVICE_OOS));
    EXPECT_TRUE(checkError("dst4/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst5/session", ErrorCode::NONE));
    TEST_FLUSH();
    oosServer.setState(OOSState()
                       .add("dst2/session"));
    EXPECT_TRUE(src.waitState(OOSState()
                             .add("dst2/session", true)
                             .add("dst3/session", false)));
    EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));
    EXPECT_TRUE(checkError("dst3/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst4/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst5/session", ErrorCode::NONE));
    TEST_FLUSH();
    {
        OOSServer oosServer2(slobrok, "oos/2", OOSState()
                             .add("dst4/session")
                             .add("dst5/session"));
        EXPECT_TRUE(src.waitSlobrok("oos/*", 2));
        EXPECT_TRUE(src.waitState(OOSState()
                                 .add("dst2/session")
                                 .add("dst4/session")
                                 .add("dst5/session")));
        EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE));
        EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst3/session", ErrorCode::NONE));
        EXPECT_TRUE(checkError("dst4/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst5/session", ErrorCode::SERVICE_OOS));
        TEST_FLUSH();
    }
    EXPECT_TRUE(src.waitSlobrok("oos/*", 1));
    EXPECT_TRUE(src.waitState(OOSState()
                             .add("dst1/session", false)
                             .add("dst2/session", true)
                             .add("dst3/session", false)
                             .add("dst4/session", false)
                             .add("dst5/session", false)));
    EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));
    EXPECT_TRUE(checkError("dst3/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst4/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst5/session", ErrorCode::NONE));
    TEST_FLUSH();
    {
        OOSServer oosServer3(slobrok, "oos/3", OOSState()
                             .add("dst2/session")
                             .add("dst4/session"));
        OOSServer oosServer4(slobrok, "oos/4", OOSState()
                             .add("dst2/session")
                             .add("dst3/session")
                             .add("dst5/session"));
        EXPECT_TRUE(src.waitSlobrok("oos/*", 3));
        EXPECT_TRUE(src.waitState(OOSState()
                                 .add("dst2/session")
                                 .add("dst3/session")
                                 .add("dst4/session")
                                 .add("dst5/session")));
        EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE));
        EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst3/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst4/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst5/session", ErrorCode::SERVICE_OOS));
        TEST_FLUSH();
        oosServer3.setState(OOSState()
                            .add("dst2/session"));
        oosServer4.setState(OOSState()
                            .add("dst1/session"));
        EXPECT_TRUE(src.waitState(OOSState()
                                 .add("dst1/session", true)
                                 .add("dst2/session", true)
                                 .add("dst3/session", false)
                                 .add("dst4/session", false)
                                 .add("dst5/session", false)));
        EXPECT_TRUE(checkError("dst1/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));
        EXPECT_TRUE(checkError("dst3/session", ErrorCode::NONE));
        EXPECT_TRUE(checkError("dst4/session", ErrorCode::NONE));
        EXPECT_TRUE(checkError("dst5/session", ErrorCode::NONE));
        TEST_FLUSH();
    }
    EXPECT_TRUE(src.waitSlobrok("oos/*", 1));
    EXPECT_TRUE(src.waitState(OOSState()
                             .add("dst1/session", false)
                             .add("dst2/session", true)
                             .add("dst3/session", false)
                             .add("dst4/session", false)
                             .add("dst5/session", false)));
    EXPECT_TRUE(checkError("dst1/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));
    EXPECT_TRUE(checkError("dst3/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst4/session", ErrorCode::NONE));
    EXPECT_TRUE(checkError("dst5/session", ErrorCode::NONE));

    h2.session.reset();
    EXPECT_TRUE(src.waitSlobrok("*/session", 4));
    EXPECT_TRUE(checkError("dst2/session", ErrorCode::SERVICE_OOS));

    _session.reset();
    TEST_DONE();
}
