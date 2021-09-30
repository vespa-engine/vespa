// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/documentapi/messagebus/iroutingpolicyfactory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

using document::DocumentTypeRepo;
using namespace documentapi;

///////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
///////////////////////////////////////////////////////////////////////////////

class MyPolicy : public mbus::IRoutingPolicy {
private:
    string _param;
public:
    MyPolicy(const string &param);
    void select(mbus::RoutingContext &ctx) override;
    void merge(mbus::RoutingContext &ctx) override;
};

MyPolicy::MyPolicy(const string &param) :
    _param(param)
{
    // empty
}

void
MyPolicy::select(mbus::RoutingContext &ctx)
{
    ctx.setError(DocumentProtocol::ERROR_POLICY_FAILURE, _param);
}

void
MyPolicy::merge(mbus::RoutingContext &ctx)
{
    (void)ctx;
    ASSERT_TRUE(false);
}

class MyFactory : public IRoutingPolicyFactory {
public:
    mbus::IRoutingPolicy::UP createPolicy(const string &param) const override;
};

mbus::IRoutingPolicy::UP
MyFactory::createPolicy(const string &param) const
{
    return std::make_unique<MyPolicy>(param);
}

mbus::Message::UP
createMessage()
{
    auto ret = std::make_unique<RemoveDocumentMessage>(document::DocumentId("id:ns:type::"));
    ret->getTrace().setLevel(9);
    return ret;
}

///////////////////////////////////////////////////////////////////////////////
//
// Tests
//
///////////////////////////////////////////////////////////////////////////////

TEST_SETUP(Test);

const vespalib::duration TIMEOUT = 600s;

int
Test::Main()
{
    TEST_INIT("policyfactory_test");

    std::shared_ptr<const DocumentTypeRepo> repo(new DocumentTypeRepo);
    mbus::Slobrok slobrok;
    mbus::TestServer
        srv(mbus::MessageBusParams().addProtocol(std::make_shared<DocumentProtocol>(repo)),
            mbus::RPCNetworkParams(slobrok.config()));
    mbus::Receptor handler;
    mbus::SourceSession::UP src = srv.mb.createSourceSession(mbus::SourceSessionParams().setReplyHandler(handler));

    mbus::Route route = mbus::Route::parse("[MyPolicy]");
    ASSERT_TRUE(src->send(createMessage(), route).isAccepted());
    mbus::Reply::UP reply = static_cast<mbus::Receptor&>(src->getReplyHandler()).getReply(TIMEOUT);
    ASSERT_TRUE(reply);
    fprintf(stderr, "%s", reply->getTrace().toString().c_str());
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)mbus::ErrorCode::UNKNOWN_POLICY, reply->getError(0).getCode());

    mbus::IProtocol * obj = srv.mb.getProtocol(DocumentProtocol::NAME);
    DocumentProtocol * protocol = dynamic_cast<DocumentProtocol*>(obj);
    ASSERT_TRUE(protocol != nullptr);
    protocol->putRoutingPolicyFactory("MyPolicy", std::make_shared<MyFactory>());

    ASSERT_TRUE(src->send(createMessage(), route).isAccepted());
    reply = static_cast<mbus::Receptor&>(src->getReplyHandler()).getReply(TIMEOUT);
    ASSERT_TRUE(reply);
    fprintf(stderr, "%s", reply->getTrace().toString().c_str());
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)DocumentProtocol::ERROR_POLICY_FAILURE, reply->getError(0).getCode());

    TEST_DONE();
}
