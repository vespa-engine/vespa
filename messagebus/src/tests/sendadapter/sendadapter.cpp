// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("sendadapter_test");

using namespace mbus;

////////////////////////////////////////////////////////////////////////////////
//
// Setup
//
////////////////////////////////////////////////////////////////////////////////

class TestProtocol : public mbus::SimpleProtocol {
private:
    mutable vespalib::Version _lastVersion;

public:
    typedef std::shared_ptr<TestProtocol> SP;
    mbus::Blob encode(const vespalib::Version &version, const mbus::Routable &routable) const override {
        _lastVersion = version;
        return mbus::SimpleProtocol::encode(version, routable);
    }
    mbus::Routable::UP decode(const vespalib::Version &version, mbus::BlobRef blob) const override {
        _lastVersion = version;
        return mbus::SimpleProtocol::decode(version, blob);
    }
    const vespalib::Version &getLastVersion() { return _lastVersion; }
};

class TestData {
public:
    Slobrok                 _slobrok;
    TestProtocol::SP        _srcProtocol;
    TestServer              _srcServer;
    SourceSession::UP       _srcSession;
    Receptor                _srcHandler;
    TestProtocol::SP        _itrProtocol;
    TestServer              _itrServer;
    IntermediateSession::UP _itrSession;
    Receptor                _itrHandler;
    TestProtocol::SP        _dstProtocol;
    TestServer              _dstServer;
    DestinationSession::UP  _dstSession;
    Receptor                _dstHandler;

public:
    TestData();
    ~TestData();
    bool start();
};

class Test : public vespalib::TestApp {
private:
    static const int TIMEOUT_SECS = 60;

    bool testVersionedSend(TestData &data,
                           const vespalib::Version &srcVersion,
                           const vespalib::Version &itrVersion,
                           const vespalib::Version &dstVersion);
    void testSendAdapters(TestData &data);

public:
    int Main() override;
};

TEST_APPHOOK(Test);

TestData::TestData() :
    _slobrok(),
    _srcProtocol(new TestProtocol()),
    _srcServer(MessageBusParams().setRetryPolicy(IRetryPolicy::SP()).addProtocol(_srcProtocol),
               RPCNetworkParams().setSlobrokConfig(_slobrok.config())),
    _srcSession(),
    _srcHandler(),
    _itrProtocol(new TestProtocol()),
    _itrServer(MessageBusParams().addProtocol(_itrProtocol),
               RPCNetworkParams().setIdentity(Identity("itr")).setSlobrokConfig(_slobrok.config())),
    _itrSession(),
    _itrHandler(),
    _dstProtocol(new TestProtocol()),
    _dstServer(MessageBusParams().addProtocol(_dstProtocol),
               RPCNetworkParams().setIdentity(Identity("dst")).setSlobrokConfig(_slobrok.config())),
    _dstSession(),
    _dstHandler()
{ }

TestData::~TestData() {}

bool
TestData::start()
{
    _srcSession = _srcServer.mb.createSourceSession(SourceSessionParams().setReplyHandler(_srcHandler));
    if (_srcSession.get() == NULL) {
        return false;
    }
    _itrSession = _itrServer.mb.createIntermediateSession(IntermediateSessionParams().setName("session").setMessageHandler(_itrHandler).setReplyHandler(_itrHandler));
    if (_itrSession.get() == NULL) {
        return false;
    }
    _dstSession = _dstServer.mb.createDestinationSession(DestinationSessionParams().setName("session").setMessageHandler(_dstHandler));
    if (_dstSession.get() == NULL) {
        return false;
    }
    if (!_srcServer.waitSlobrok("*/session", 2u)) {
        return false;
    }
    return true;
}

int
Test::Main()
{
    TEST_INIT("sendadapter_test");

    TestData data;
    ASSERT_TRUE(data.start());

    testSendAdapters(data); TEST_FLUSH();

    TEST_DONE();
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

void
Test::testSendAdapters(TestData &data)
{
    std::vector<vespalib::Version> versions;
    versions.push_back(vespalib::Version(5, 0));
    versions.push_back(vespalib::Version(5, 1));

    for (std::vector<vespalib::Version>::const_iterator srcVersion = versions.begin();
         srcVersion != versions.end(); ++srcVersion)
    {
        for (std::vector<vespalib::Version>::const_iterator itrVersion = versions.begin();
             itrVersion != versions.end(); ++itrVersion)
        {
            for (std::vector<vespalib::Version>::const_iterator dstVersion = versions.begin();
                 dstVersion != versions.end(); ++dstVersion)
            {
                EXPECT_TRUE(testVersionedSend(data, *srcVersion, *itrVersion, *dstVersion));
            }
        }
    }
}

////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

bool
Test::testVersionedSend(TestData &data,
                         const vespalib::Version &srcVersion,
                         const vespalib::Version &itrVersion,
                         const vespalib::Version &dstVersion)
{
    LOG(info, "Sending from %s through %s to %s.",
        srcVersion.toString().c_str(), itrVersion.toString().c_str(), dstVersion.toString().c_str());
    data._srcServer.net.setVersion(srcVersion);
    data._itrServer.net.setVersion(itrVersion);
    data._dstServer.net.setVersion(dstVersion);

    Message::UP msg(new SimpleMessage("foo"));
    msg->getTrace().setLevel(9);
    if (!EXPECT_TRUE(data._srcSession->send(std::move(msg), Route::parse("itr/session dst/session")).isAccepted())) {
        return false;
    }
    msg = data._itrHandler.getMessage(TIMEOUT_SECS);
    if (!EXPECT_TRUE(msg.get() != NULL)) {
        return false;
    }
    LOG(info, "Message version %s serialized at source.",
        data._srcProtocol->getLastVersion().toString().c_str());
    vespalib::Version minVersion = std::min(srcVersion, itrVersion);
    if (!EXPECT_TRUE(minVersion == data._srcProtocol->getLastVersion())) {
        return false;
    }

    LOG(info, "Message version %s reached intermediate.",
        data._itrProtocol->getLastVersion().toString().c_str());
    if (!EXPECT_TRUE(minVersion == data._itrProtocol->getLastVersion())) {
        return false;
    }
    data._itrSession->forward(std::move(msg));
    msg = data._dstHandler.getMessage(TIMEOUT_SECS);
    if (!EXPECT_TRUE(msg.get() != NULL)) {
        return false;
    }
    LOG(info, "Message version %s serialized at intermediate.",
        data._itrProtocol->getLastVersion().toString().c_str());
    minVersion = std::min(itrVersion, dstVersion);
    if (!EXPECT_TRUE(minVersion == data._itrProtocol->getLastVersion())) {
        return false;
    }

    LOG(info, "Message version %s reached destination.",
        data._dstProtocol->getLastVersion().toString().c_str());
    if (!EXPECT_TRUE(minVersion == data._dstProtocol->getLastVersion())) {
        return false;
    }
    Reply::UP reply(new SimpleReply("bar"));
    reply->swapState(*msg);
    data._dstSession->reply(std::move(reply));
    reply = data._itrHandler.getReply();
    if (!EXPECT_TRUE(reply.get() != NULL)) {
        return false;
    }
    LOG(info, "Reply version %s serialized at destination.",
        data._dstProtocol->getLastVersion().toString().c_str());
    if (!EXPECT_TRUE(minVersion == data._dstProtocol->getLastVersion())) {
        return false;
    }

    LOG(info, "Reply version %s reached intermediate.",
        data._itrProtocol->getLastVersion().toString().c_str());
    if (!EXPECT_TRUE(minVersion == data._itrProtocol->getLastVersion())) {
        return false;
    }
    data._itrSession->forward(std::move(reply));
    reply = data._srcHandler.getReply();
    if (!EXPECT_TRUE(reply.get() != NULL)) {
        return false;
    }
    LOG(info, "Reply version %s serialized at intermediate.",
        data._dstProtocol->getLastVersion().toString().c_str());
    minVersion = std::min(srcVersion, itrVersion);
    if (!EXPECT_TRUE(minVersion == data._itrProtocol->getLastVersion())) {
        return false;
    }

    LOG(info, "Reply version %s reached source.",
        data._srcProtocol->getLastVersion().toString().c_str());
    if (!EXPECT_TRUE(minVersion == data._srcProtocol->getLastVersion())) {
        return false;
    }
    return true;
}
