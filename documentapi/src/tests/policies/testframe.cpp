// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testframe.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/network/rpcnetwork.h>
#include <vespa/messagebus/sendproxy.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>

#include <vespa/log/log.h>
LOG_SETUP(".testframe");

using document::DocumentTypeRepo;
using namespace documentapi;

class MyServiceAddress : public mbus::IServiceAddress {
private:
    string _address;

public:
    MyServiceAddress(const string &address) :
        _address(address) {
        // empty
    }

    const string &getAddress() {
        return _address;
    }
};

class MyNetwork : public mbus::RPCNetwork {
private:
    std::vector<mbus::RoutingNode*> _nodes;

public:
    MyNetwork(const mbus::RPCNetworkParams &params) :
        mbus::RPCNetwork(params),
        _nodes() {
        // empty
    }

    bool allocServiceAddress(mbus::RoutingNode &recipient) override {
        string hop = recipient.getRoute().getHop(0).toString();
        recipient.setServiceAddress(mbus::IServiceAddress::UP(new MyServiceAddress(hop)));
        return true;
    }

    void freeServiceAddress(mbus::RoutingNode &recipient) override {
        recipient.setServiceAddress(mbus::IServiceAddress::UP());
    }

    void send(const mbus::Message &, const std::vector<mbus::RoutingNode*> &nodes) override {
        _nodes.insert(_nodes.begin(), nodes.begin(), nodes.end());
    }

    void removeNodes(std::vector<mbus::RoutingNode*> &nodes) {
        nodes.insert(nodes.begin(), _nodes.begin(), _nodes.end());
        _nodes.clear();
    }
};

TestFrame::TestFrame(const std::shared_ptr<const DocumentTypeRepo> &repo, const string &ident) :
    _identity(ident),
    _slobrok(new mbus::Slobrok()),
    _set(),
    _net(new MyNetwork(mbus::RPCNetworkParams()
                       .setIdentity(mbus::Identity(ident))
                       .setSlobrokConfig(_slobrok->config()))),
    _mbus(new mbus::MessageBus(*_net, mbus::MessageBusParams()
                               .addProtocol(mbus::IProtocol::SP(new DocumentProtocol(_set, repo))))),
    _msg(),
    _hop(mbus::HopSpec("foo", "bar")),
    _handler()
{
    // empty
}

TestFrame::TestFrame(TestFrame &frame) :
    mbus::IReplyHandler(),
    _identity(frame._identity),
    _slobrok(frame._slobrok),
    _net(frame._net),
    _mbus(frame._mbus),
    _msg(),
    _hop(mbus::HopSpec("baz", "cox")),
    _handler()
{
    // empty
}

TestFrame::~TestFrame()
{
    // empty
}

void
TestFrame::setHop(const mbus::HopSpec &hop)
{
    _hop = hop;
    _mbus->setupRouting(mbus::RoutingSpec().addTable(mbus::RoutingTableSpec(DocumentProtocol::NAME).addHop(_hop)));
}

bool
TestFrame::select(std::vector<mbus::RoutingNode*> &selected, uint32_t numExpected)
{
    _msg->setRoute(mbus::Route::parse(_hop.getName()));
    _msg->pushHandler(*this);
    mbus::SendProxy &proxy = *(new mbus::SendProxy(*_mbus, *_net, NULL)); // deletes self
    proxy.handleMessage(std::move(_msg));

    static_cast<MyNetwork&>(*_net).removeNodes(selected);
    if (selected.size() != numExpected) {
        LOG(error, "Expected %d recipients, got %d.", numExpected, (uint32_t)selected.size());
        return false;
    }
    return true;
}

bool
TestFrame::testSelect(const std::vector<string> &expected)
{
    std::vector<mbus::RoutingNode*> selected;
    if (!select(selected, expected.size())) {
        LOG(error, "Failed to select recipients.");
        for (size_t i = 0; i < selected.size(); ++i) {
            LOG(error, "Selected: %s",
                selected[i]->getRoute().toString().c_str());
        }
        return false;
    }
    for (std::vector<mbus::RoutingNode*>::iterator it = selected.begin();
         it != selected.end(); ++it)
    {
        string route = (*it)->getRoute().toString();
        if (find(expected.begin(), expected.end(), route) == expected.end()) {
            LOG(error, "Recipient '%s' not selected.", route.c_str());
        }
        (*it)->handleReply(mbus::Reply::UP(new mbus::EmptyReply()));
    }
    if (_handler.getReply(600).get() == NULL) {
        LOG(error, "Reply not propagated to handler.");
        return false;
    }
    return true;
}

bool
TestFrame::testMergeError(const ReplyMap &replies, const std::vector<uint32_t> &expectedErrors)
{
    return testMerge(replies, expectedErrors, StringList());
}

bool
TestFrame::testMergeOk(const ReplyMap &replies, const std::vector<string> &allowedValues)
{
    return testMerge(replies, UIntList(), allowedValues);
}

bool
TestFrame::testMerge(const ReplyMap &replies,
                     const std::vector<uint32_t> &expectedErrors,
                     const std::vector<string> &allowedValues)
{
    std::vector<mbus::RoutingNode*> selected;
    if (!select(selected, replies.size())) {
        return false;
    }

    for (std::vector<mbus::RoutingNode*>::iterator it = selected.begin();
         it != selected.end(); ++it)
    {
        string route = (*it)->getRoute().toString();
        ReplyMap::const_iterator mip = replies.find(route);
        if (mip == replies.end()) {
            LOG(error, "Recipient '%s' not expected.", route.c_str());
            return false;
        }

        mbus::Reply::UP ret(new mbus::SimpleReply(route));
        if (mip->second != mbus::ErrorCode::NONE) {
            ret->addError(mbus::Error(mip->second, route));
        }
        (*it)->handleReply(std::move(ret));
    }

    mbus::Reply::UP reply = _handler.getReply(600);
    if (reply.get() == NULL) {
        LOG(error, "Reply not propagated to handler.");
        return false;
    }
    if (!expectedErrors.empty()) {
        if (expectedErrors.size() != reply->getNumErrors()) {
            LOG(error, "Expected %d errors, got %d.", (uint32_t)expectedErrors.size(), reply->getNumErrors());
            return false;
        }
        for (uint32_t i = 0; i < expectedErrors.size(); ++i) {
            uint32_t err = reply->getError(i).getCode();
            if (std::find(expectedErrors.begin(), expectedErrors.end(), err) == expectedErrors.end()) {
                LOG(error, "Expected error code %d not found.", err);
                return false;
            }
        }
    } else if (reply->hasErrors()) {
        LOG(error, "Got %d unexpected error(s):", reply->getNumErrors());
        for(uint32_t i = 0; i < reply->getNumErrors(); ++i) {
            LOG(error, "%d. %s", i + 1, reply->getError(i).toString().c_str());
        }
        return false;
    }
    if (!allowedValues.empty()) {
        if (mbus::SimpleProtocol::REPLY != reply->getType()) {
            LOG(error, "Expected reply type %d, got %d.", mbus::SimpleProtocol::REPLY, reply->getType());
            return false;
        }
        string val = static_cast<mbus::SimpleReply&>(*reply).getValue();
        if (std::find(allowedValues.begin(), allowedValues.end(), val) == allowedValues.end()) {
            LOG(error, "Value '%s' not allowed.", val.c_str());
            return false;
        }
    } else {
        if (0 != reply->getType()) {
            LOG(error, "Expected reply type %d, got %d.", 0, reply->getType());
            return false;
        }
    }
    return true;
}

bool
TestFrame::testMergeOneReply(const string &recipient)
{
    if (!testSelect(StringList().add(recipient))) {
        return false;
    }

    ReplyMap replies;
    replies[recipient] = mbus::ErrorCode::NONE;
    if (!testMergeOk(replies, StringList().add(recipient))) {
        LOG(error, "Failed to merge reply with no error.");
        return false;
    }

    replies[recipient] = mbus::ErrorCode::TRANSIENT_ERROR;
    if (!testMergeError(replies, UIntList().add(mbus::ErrorCode::TRANSIENT_ERROR))) {
        LOG(error, "Failed to merge reply with transient error.");
        return false;
    }

    return true;
}

bool
TestFrame::testMergeTwoReplies(const string &recipientOne, const string &recipientTwo)
{
    if (!testSelect(StringList().add(recipientOne).add(recipientTwo))) {
        return false;
    }

    ReplyMap replies;
    replies[recipientOne] = mbus::ErrorCode::NONE;
    replies[recipientTwo] = mbus::ErrorCode::NONE;
    if (!testMergeOk(replies, StringList().add(recipientOne).add(recipientTwo))) {
        LOG(error, "Failed to merge two replies with no error.");
        return false;
    }

    replies[recipientOne] = mbus::ErrorCode::TRANSIENT_ERROR;
    replies[recipientTwo] = mbus::ErrorCode::NONE;
    if (!testMergeError(replies, UIntList().add(mbus::ErrorCode::TRANSIENT_ERROR))) {
        LOG(error, "Failed to merge two replies where one has transient error.");
        return false;
    }

    replies[recipientOne] = mbus::ErrorCode::TRANSIENT_ERROR;
    replies[recipientTwo] = mbus::ErrorCode::TRANSIENT_ERROR;
    if (!testMergeError(replies, UIntList()
                        .add(mbus::ErrorCode::TRANSIENT_ERROR)
                        .add(mbus::ErrorCode::TRANSIENT_ERROR))) {
        LOG(error, "Failed to merge two replies where both have transient errors.");
        return false;
    }

    replies[recipientOne] = mbus::ErrorCode::NONE;
    replies[recipientTwo] = DocumentProtocol::ERROR_MESSAGE_IGNORED;
    if (!testMergeOk(replies, StringList().add(recipientOne))) {
        LOG(error, "Failed to merge two replies where second should be ignored.");
        return false;
    }

    replies[recipientOne] = DocumentProtocol::ERROR_MESSAGE_IGNORED;
    replies[recipientTwo] = mbus::ErrorCode::NONE;
    if (!testMergeOk(replies, StringList().add(recipientTwo))) {
        LOG(error, "Failed to merge two replies where first should be ignored.");
        return false;
    }

    replies[recipientOne] = DocumentProtocol::ERROR_MESSAGE_IGNORED;
    replies[recipientTwo] = DocumentProtocol::ERROR_MESSAGE_IGNORED;
    if (!testMergeError(replies, UIntList()
                        .add(DocumentProtocol::ERROR_MESSAGE_IGNORED)
                        .add(DocumentProtocol::ERROR_MESSAGE_IGNORED))) {
        LOG(error, "Failed to merge two replies where both can be ignored.");
        return false;
    }

    return true;
}

bool
TestFrame::waitSlobrok(const string &pattern, uint32_t cnt)
{
    for (uint32_t i = 0; i < 1000; ++i) {
        slobrok::api::IMirrorAPI::SpecList res = _net->getMirror().lookup(pattern);
        if (res.size() == cnt) {
            return true;
        }
        FastOS_Thread::Sleep(10);
    }
    LOG(error, "Slobrok failed to resolve '%s' to %d recipients in time.", pattern.c_str(), cnt);
    return false;
}

SystemStateHandle
TestFrame::getSystemState()
{
    mbus::IProtocol * protocol = _mbus->getProtocol(DocumentProtocol::NAME);
    return SystemStateHandle(static_cast<DocumentProtocol&>(*protocol).getSystemState());
}

void
TestFrame::handleReply(mbus::Reply::UP reply)
{
    _msg = reply->getMessage();
    _handler.handleReply(std::move(reply));
}
