// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messagebus.h"
#include "messenger.h"
#include "emptyreply.h"
#include "errorcode.h"
#include "sendproxy.h"
#include "protocolrepository.h"
#include <vespa/messagebus/network/inetwork.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/gate.h>

#include <vespa/log/log.h>
LOG_SETUP(".messagebus");

using vespalib::make_string;
using namespace std::chrono_literals;

namespace {

/**
 * Implements a task for running the resender in the messenger thread. This task
 * acts as a proxy for the resender, allowing the task to be deleted without
 * affecting the resender itself.
 */
class ResenderTask : public mbus::Messenger::ITask {
private:
    mbus::Resender *_resender;

public:
    ResenderTask(mbus::Resender &resender)
        : _resender(&resender)
    {
        // empty
    }

    void run() override {
        _resender->resendScheduled();
    }

    uint8_t priority() const override {
        return 255;
    }
};

/**
 * Implements a task for monitoring shutdown of the messenger thread. This task
 * helps to determine whether or not there is any work left in either the
 * messenger or network thread.
 */
class ShutdownTask : public mbus::Messenger::ITask {
private:
    mbus::INetwork  &_net;
    mbus::Messenger &_msn;
    bool            &_done;
    vespalib::Gate  &_gate;

public:
    ShutdownTask(mbus::INetwork &net, mbus::Messenger &msn,
                 bool &done, vespalib::Gate &gate)
        : _net(net),
          _msn(msn),
          _done(done),
          _gate(gate)
    { }

    ~ShutdownTask() override {
        _gate.countDown();
    }

    void run() override {
        _net.postShutdownHook();
        _done = _msn.isEmpty();
    }

    uint8_t priority() const override {
        return 255;
    }
};

} // anonymous

namespace mbus {

MessageBus::MessageBus(INetwork &net, ProtocolSet protocols) :
    _network(net),
    _lock(),
    _routingTables(),
    _sessions(),
    _protocolRepository(std::make_unique<ProtocolRepository>()),
    _msn(std::make_unique<Messenger>()),
    _resender(),
    _maxPendingCount(0),
    _maxPendingSize(0),
    _pendingCount(0),
    _pendingSize(0)
{
    MessageBusParams params;
    while (!protocols.empty()) {
        IProtocol::SP protocol = protocols.extract();
        if (protocol) {
            params.addProtocol(protocol);
        }
    }
    setup(params);
}

MessageBus::MessageBus(INetwork &net, const MessageBusParams &params) :
    _network(net),
    _lock(),
    _routingTables(),
    _sessions(),
    _protocolRepository(std::make_unique<ProtocolRepository>()),
    _msn(std::make_unique<Messenger>()),
    _resender(),
    _maxPendingCount(params.getMaxPendingCount()),
    _maxPendingSize(params.getMaxPendingSize()),
    _pendingCount(0),
    _pendingSize(0)
{
    setup(params);
}

MessageBus::~MessageBus()
{
    // all sessions must have been destroyed prior to this,
    // so no more traffic from clients
    _msn->discardRecurrentTasks(); // no more traffic from recurrent tasks
    _network.shutdown(); // no more traffic from network

    bool done = false;
    while (!done) {
        vespalib::Gate gate;
        _msn->enqueue(std::make_unique<ShutdownTask>(_network, *_msn, done, gate));
        gate.await();
    }
}

void
MessageBus::setup(const MessageBusParams &params)
{
    // Add all known protocols to the repository.
    for (uint32_t i = 0, len = params.getNumProtocols(); i < len; ++i) {
        _protocolRepository->putProtocol(params.getProtocol(i));
    }

    // Attach and start network.
    _network.attach(*this);
    if (!_network.start()) {
        throw vespalib::NetworkSetupFailureException("Failed to start network.");
    }
    if (!_network.waitUntilReady(120s)) {
        throw vespalib::NetworkSetupFailureException("Network failed to become ready in time.");
    }

    // Start messenger.
    IRetryPolicy::SP retryPolicy = params.getRetryPolicy();
    if (retryPolicy) {
        _resender = std::make_unique<Resender>(retryPolicy);

        _msn->addRecurrentTask(std::make_unique<ResenderTask>(*_resender));
    }
    if (!_msn->start()) {
        throw vespalib::NetworkSetupFailureException("Failed to start messenger.");
    }
}

SourceSession::UP
MessageBus::createSourceSession(IReplyHandler &handler)
{
    return createSourceSession(SourceSessionParams().setReplyHandler(handler));
}

SourceSession::UP
MessageBus::createSourceSession(IReplyHandler &handler,
                                const SourceSessionParams &params)
{
    return createSourceSession(SourceSessionParams(params).setReplyHandler(handler));
}

SourceSession::UP
MessageBus::createSourceSession(const SourceSessionParams &params)
{
    return SourceSession::UP(new SourceSession(*this, params));
}

IntermediateSession::UP
MessageBus::createIntermediateSession(const string &name,
                                      bool broadcastName,
                                      IMessageHandler &msgHandler,
                                      IReplyHandler &replyHandler)
{
    return createIntermediateSession(IntermediateSessionParams()
                                     .setName(name)
                                     .setBroadcastName(broadcastName)
                                     .setMessageHandler(msgHandler)
                                     .setReplyHandler(replyHandler));
}

IntermediateSession::UP
MessageBus::createIntermediateSession(const IntermediateSessionParams &params)
{
    std::lock_guard guard(_lock);
    IntermediateSession::UP ret(new IntermediateSession(*this, params));
    _sessions[params.getName()] = ret.get();
    if (params.getBroadcastName()) {
        _network.registerSession(params.getName());
    }
    return ret;
}

DestinationSession::UP
MessageBus::createDestinationSession(const string &name,
                                     bool broadcastName,
                                     IMessageHandler &handler)
{
    return createDestinationSession(DestinationSessionParams()
                                    .setName(name)
                                    .setBroadcastName(broadcastName)
                                    .setMessageHandler(handler));
}

DestinationSession::UP
MessageBus::createDestinationSession(const DestinationSessionParams &params)
{
    std::lock_guard guard(_lock);
    DestinationSession::UP ret(new DestinationSession(*this, params));
    _sessions[params.getName()] = ret.get();
    if (params.getBroadcastName()) {
        _network.registerSession(params.getName());
    }
    return ret;
}

void
MessageBus::unregisterSession(const string &sessionName)
{
    std::lock_guard guard(_lock);
    _network.unregisterSession(sessionName);
    _sessions.erase(sessionName);
}

RoutingTable::SP
MessageBus::getRoutingTable(const string &protocol)
{
    typedef std::map<string, RoutingTable::SP>::iterator ITR;
    std::lock_guard guard(_lock);
    ITR itr = _routingTables.find(protocol);
    if (itr == _routingTables.end()) {
        return RoutingTable::SP(); // not found
    }
    return itr->second;
}

IRoutingPolicy::SP
MessageBus::getRoutingPolicy(const string &protocolName,
                             const string &policyName,
                             const string &policyParam)
{
    return _protocolRepository->getRoutingPolicy(protocolName, policyName, policyParam);
}

void
MessageBus::sync()
{
    _msn->sync();
    _network.sync(); // should not be necessary, as msn is intermediate
}

void
MessageBus::handleMessage(Message::UP msg)
{
    if (_resender && msg->hasBucketSequence()) {
        deliverError(std::move(msg), ErrorCode::SEQUENCE_ERROR,
                     "Bucket sequences not supported when resender is enabled.");
        return;
    }
    SendProxy &proxy = *(new SendProxy(*this, _network, _resender.get())); // deletes self
    _msn->deliverMessage(std::move(msg), proxy);
}

bool
MessageBus::setupRouting(const RoutingSpec &spec)
{
    std::map<string, RoutingTable::SP> rtm;
    for (uint32_t i = 0; i < spec.getNumTables(); ++i) {
        const RoutingTableSpec &cfg = spec.getTable(i);
        if (getProtocol(cfg.getProtocol()) == nullptr) { // protocol not found
            LOG(info, "Protocol '%s' is not supported, ignoring routing table.", cfg.getProtocol().c_str());
            continue;
        }
        rtm[cfg.getProtocol()] = std::make_shared<RoutingTable>(cfg);
    }
    {
        std::lock_guard guard(_lock);
        std::swap(_routingTables, rtm);
    }
    _protocolRepository->clearPolicyCache();
    return true;
}

IProtocol *
MessageBus::getProtocol(const string &name)
{
    return _protocolRepository->getProtocol(name);
}

IProtocol::SP
MessageBus::putProtocol(const IProtocol::SP & protocol)
{
    return _protocolRepository->putProtocol(protocol);
}

bool
MessageBus::checkPending(Message &msg)
{
    bool busy = false;
    const uint32_t size = msg.getApproxSize();
    {
        constexpr auto relaxed = std::memory_order_relaxed;
        const uint32_t maxCount = _maxPendingCount.load(relaxed);
        const uint32_t maxSize = _maxPendingSize.load(relaxed);
        if (maxCount > 0 || maxSize > 0) {
            busy = ((maxCount > 0 && _pendingCount.load(relaxed) >= maxCount) ||
                    (maxSize > 0 && _pendingSize.load(relaxed) >= maxSize));
            if (!busy) {
                _pendingCount.fetch_add(1, relaxed);
                _pendingSize.fetch_add(size, relaxed);
            }
        }
    }
    if (busy) {
        return false;
    }
    msg.setContext(Context(static_cast<uint64_t>(size)));
    msg.pushHandler(*this, *this);
    return true;
}

void
MessageBus::handleReply(Reply::UP reply)
{
    _pendingCount.fetch_sub(1, std::memory_order_relaxed);
    _pendingSize.fetch_sub(reply->getContext().value.UINT64,
                           std::memory_order_relaxed);
    IReplyHandler &handler = reply->getCallStack().pop(*reply);
    deliverReply(std::move(reply), handler);
}

void
MessageBus::handleDiscard(Context ctx)
{
    _pendingCount.fetch_sub(1, std::memory_order_relaxed);
    _pendingSize.fetch_sub(ctx.value.UINT64, std::memory_order_relaxed);
}

void
MessageBus::deliverMessage(Message::UP msg, const string &session)
{
    IMessageHandler *msgHandler = nullptr;
    {
        std::lock_guard guard(_lock);
        std::map<string, IMessageHandler*>::iterator it = _sessions.find(session);
        if (it != _sessions.end()) {
            msgHandler = it->second;
        }
    }
    if (msgHandler == nullptr) {
        deliverError(std::move(msg), ErrorCode::UNKNOWN_SESSION,
                     make_string("Session '%s' does not exist.", session.c_str()));
    } else if (!checkPending(*msg)) {
        deliverError(std::move(msg), ErrorCode::SESSION_BUSY,
                     make_string("Session '%s' is busy, try again later.", session.c_str()));
    } else {
        _msn->deliverMessage(std::move(msg), *msgHandler);
    }
}

void
MessageBus::deliverError(Message::UP msg, uint32_t errCode, const string &errMsg)
{
    auto reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(errCode, errMsg));

    IReplyHandler &replyHandler = reply->getCallStack().pop(*reply);
    deliverReply(std::move(reply), replyHandler);
}

void
MessageBus::deliverReply(Reply::UP reply, IReplyHandler &handler)
{
    _msn->deliverReply(std::move(reply), handler);
}

string
MessageBus::getConnectionSpec() const
{
    return _network.getConnectionSpec();
}

void
MessageBus::setMaxPendingCount(uint32_t maxCount)
{
    _maxPendingCount.store(maxCount, std::memory_order_relaxed);
}

void
MessageBus::setMaxPendingSize(uint32_t maxSize)
{
    _maxPendingSize.store(maxSize, std::memory_order_relaxed);
}

} // namespace mbus
