// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "communicationmanager.h"
#include "fnetlistener.h"
#include "rpcrequestwrapper.h"
#include <vespa/documentapi/messagebus/messages/wrongdistributionreply.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/storage/common/bucket_resolver.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/storageserver/configurable_bucket_resolver.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/bufferedlogger.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

LOG_SETUP(".communication.manager");

using vespalib::make_string;
using document::FixedBucketSpaces;

namespace storage {

Queue::Queue() = default;
Queue::~Queue() = default;

bool Queue::getNext(std::shared_ptr<api::StorageMessage>& msg, int timeout) {
    vespalib::MonitorGuard sync(_queueMonitor);
    bool first = true;
    while (true) { // Max twice
        if (!_queue.empty()) {
            LOG(spam, "Picking message from queue");
            msg = std::move(_queue.front());
            _queue.pop();
            return true;
        }
        if (timeout == 0 || !first) {
            return false;
        }
        sync.wait(timeout);
        first = false;
    }

    return false;
}

void Queue::enqueue(std::shared_ptr<api::StorageMessage> msg) {
    vespalib::MonitorGuard sync(_queueMonitor);
    _queue.emplace(std::move(msg));
    sync.unsafeSignalUnlock();
}

void Queue::signal() {
    vespalib::MonitorGuard sync(_queueMonitor);
    sync.unsafeSignalUnlock();
}

size_t Queue::size() const {
    vespalib::MonitorGuard sync(_queueMonitor);
    return _queue.size();
}

StorageTransportContext::StorageTransportContext(std::unique_ptr<documentapi::DocumentMessage> msg)
    : _docAPIMsg(std::move(msg))
{ }

StorageTransportContext::StorageTransportContext(std::unique_ptr<mbusprot::StorageCommand> msg)
    : _storageProtocolMsg(std::move(msg))
{ }

StorageTransportContext::StorageTransportContext(std::unique_ptr<RPCRequestWrapper> request)
    : _request(std::move(request))
{ }

StorageTransportContext::~StorageTransportContext() { }

void
CommunicationManager::receiveStorageReply(const std::shared_ptr<api::StorageReply>& reply)
{
    assert(reply.get());
    enqueue(reply);
}

namespace {
    vespalib::string getNodeId(StorageComponent& sc) {
        vespalib::asciistream ost;
        ost << sc.getClusterName() << "/" << sc.getNodeType() << "/" << sc.getIndex();
        return ost.str();
    }

    framework::SecondTime TEN_MINUTES(600);

}

void
CommunicationManager::handleMessage(std::unique_ptr<mbus::Message> msg)
{
    MBUS_TRACE(msg->getTrace(), 4, getNodeId(_component)
               + " CommunicationManager: Received message from message bus");
    // Relaxed load since we're not doing any dependent reads that aren't
    // already covered by some other form of explicit synchronization.
    if (_closed.load(std::memory_order_relaxed)) {
        LOG(debug, "Not handling command of type %d as we have closed down", msg->getType());
        MBUS_TRACE(msg->getTrace(), 6, "Communication manager: Failing message as we are closed");
        std::unique_ptr<mbus::Reply> reply(new mbus::EmptyReply());
        reply->addError(mbus::Error(documentapi::DocumentProtocol::ERROR_ABORTED, "Node shutting down"));
        msg->swapState(*reply);
        _messageBusSession->reply(std::move(reply));
        return;
    }
    const vespalib::string & protocolName = msg->getProtocol();

    if (protocolName == documentapi::DocumentProtocol::NAME) {
        std::unique_ptr<documentapi::DocumentMessage> docMsgPtr(static_cast<documentapi::DocumentMessage*>(msg.release()));

        assert(docMsgPtr.get());

        std::unique_ptr<api::StorageCommand> cmd;
        try {
            cmd = _docApiConverter.toStorageAPI(static_cast<documentapi::DocumentMessage&>(*docMsgPtr), _component.getTypeRepo());
        } catch (document::UnknownBucketSpaceException& e) {
            fail_with_unresolvable_bucket_space(std::move(docMsgPtr), e.getMessage());
            return;
        }

        if (!cmd.get()) {
            LOGBM(warning, "Unsupported message: StorageApi could not convert message of type %d to a storageapi message",
                  docMsgPtr->getType());
            _metrics.convertToStorageAPIFailures.inc();
            return;
        }

        cmd->setTrace(docMsgPtr->getTrace());
        cmd->setTransportContext(std::unique_ptr<api::TransportContext>(new StorageTransportContext(std::move(docMsgPtr))));

        enqueue(std::move(cmd));
    } else if (protocolName == mbusprot::StorageProtocol::NAME) {
        std::unique_ptr<mbusprot::StorageCommand> storMsgPtr(static_cast<mbusprot::StorageCommand*>(msg.release()));

        assert(storMsgPtr.get());

        //TODO: Can it be moved ?
        std::shared_ptr<api::StorageCommand> cmd = storMsgPtr->getCommand();
        cmd->setTimeout(storMsgPtr->getTimeRemaining());
        cmd->setTrace(storMsgPtr->getTrace());
        cmd->setTransportContext(std::unique_ptr<api::TransportContext>(new StorageTransportContext(std::move(storMsgPtr))));

        enqueue(std::move(cmd));
    } else {
        LOGBM(warning, "Received unsupported message type %d for protocol '%s'",
              msg->getType(), msg->getProtocol().c_str());
    }
}

void
CommunicationManager::handleReply(std::unique_ptr<mbus::Reply> reply)
{
    MBUS_TRACE(reply->getTrace(), 4, getNodeId(_component) + "Communication manager: Received reply from message bus");
    // Relaxed load since we're not doing any dependent reads that aren't
    // already covered by some other form of explicit synchronization.
    if (_closed.load(std::memory_order_relaxed)) {
        LOG(debug, "Not handling reply of type %d as we have closed down", reply->getType());
        return;
    }
    LOG(spam, "Got reply of type %d, trace is %s",
        reply->getType(), reply->getTrace().toString().c_str());
    // EmptyReply must be converted to real replies before processing.
    if (reply->getType() == 0) {
        std::unique_ptr<mbus::Message> message(reply->getMessage());

        if (message.get()) {
            std::unique_ptr<mbus::Reply> convertedReply;

            const vespalib::string& protocolName = message->getProtocol();
            if (protocolName == documentapi::DocumentProtocol::NAME) {
                convertedReply = static_cast<documentapi::DocumentMessage &>(*message).createReply();
            } else if (protocolName == mbusprot::StorageProtocol::NAME) {
                std::shared_ptr<api::StorageReply> repl(
                        static_cast<mbusprot::StorageCommand &>(*message).getCommand()->makeReply());
                mbusprot::StorageReply::UP sreply(new mbusprot::StorageReply(repl));

                if (reply->hasErrors()) {
                    // Convert only the first error since storageapi only
                    // supports one return code.
                    uint32_t mbuscode = reply->getError(0).getCode();
                    api::ReturnCode::Result code((api::ReturnCode::Result) mbuscode);
                    // Encode mbuscode into message not to lose it
                    sreply->getReply()->setResult(storage::api::ReturnCode(
                                code,
                                mbus::ErrorCode::getName(mbuscode)
                                + vespalib::string(": ")
                                + reply->getError(0).getMessage()
                                + vespalib::string(" (from ")
                                + reply->getError(0).getService()
                                + vespalib::string(")")));
                }
                convertedReply = std::move(sreply);
            } else {
                LOG(warning, "Received reply of unhandled protocol '%s'", protocolName.c_str());
                return;
            }

            convertedReply->swapState(*reply);
            convertedReply->setMessage(std::move(message));
            reply = std::move(convertedReply);
        }
        if (reply->getType() == 0) {
            LOG(warning, "Failed to convert empty reply by reflecting on local message copy.");
            return;
        }
    }

    if (reply->getContext().value.UINT64 != FORWARDED_MESSAGE) {
        const vespalib::string& protocolName = reply->getProtocol();

        if (protocolName == documentapi::DocumentProtocol::NAME) {
            std::shared_ptr<api::StorageCommand> originalCommand;
            {
                vespalib::LockGuard lock(_messageBusSentLock);
                typedef std::map<api::StorageMessage::Id, api::StorageCommand::SP> MessageMap;
                MessageMap::iterator iter(_messageBusSent.find(reply->getContext().value.UINT64));
                if (iter != _messageBusSent.end()) {
                    originalCommand.swap(iter->second);
                    _messageBusSent.erase(iter);
                } else {
                    LOG(warning, "Failed to convert reply - original sent command doesn't exist");
                    return;
                }
            }

            std::shared_ptr<api::StorageReply> sar(
                    _docApiConverter.toStorageAPI(static_cast<documentapi::DocumentReply&>(*reply), *originalCommand));

            if (sar.get()) {
                sar->setTrace(reply->getTrace());
                receiveStorageReply(sar);
            }
        } else if (protocolName == mbusprot::StorageProtocol::NAME) {
            mbusprot::StorageReply* sr(static_cast<mbusprot::StorageReply*>(reply.get()));
            sr->getReply()->setTrace(reply->getTrace());
            receiveStorageReply(sr->getReply());
        } else {
            LOGBM(warning, "Received unsupported reply type %d for protocol '%s'.",
                  reply->getType(), reply->getProtocol().c_str());
        }
    }
}

void CommunicationManager::fail_with_unresolvable_bucket_space(
        std::unique_ptr<documentapi::DocumentMessage> msg,
        const vespalib::string& error_message)
{
    LOG(debug, "Could not map DocumentAPI message to internal bucket: %s", error_message.c_str());
    MBUS_TRACE(msg->getTrace(), 6, "Communication manager: Failing message as its document type has no known bucket space mapping");
    std::unique_ptr<mbus::Reply> reply(new mbus::EmptyReply());
    reply->addError(mbus::Error(documentapi::DocumentProtocol::ERROR_REJECTED, error_message));
    msg->swapState(*reply);
    _metrics.bucketSpaceMappingFailures.inc();
    _messageBusSession->reply(std::move(reply));
}

namespace {

struct PlaceHolderBucketResolver : public BucketResolver {
    virtual document::Bucket bucketFromId(const document::DocumentId &) const override {
        return document::Bucket(FixedBucketSpaces::default_space(), document::BucketId(0));
    }
    virtual document::BucketSpace bucketSpaceFromName(const vespalib::string &) const override {
        return FixedBucketSpaces::default_space();
    }
    virtual vespalib::string nameFromBucketSpace(const document::BucketSpace &bucketSpace) const override {
        assert(bucketSpace == FixedBucketSpaces::default_space());
        return FixedBucketSpaces::to_string(bucketSpace);
    }
};

}

CommunicationManager::CommunicationManager(StorageComponentRegister& compReg, const config::ConfigUri & configUri)
    : StorageLink("Communication manager"),
      _component(compReg, "communicationmanager"),
      _metrics(_component.getLoadTypes()->getMetricLoadTypes()),
      _listener(),
      _eventQueue(),
      _mbus(),
      _count(0),
      _configUri(configUri),
      _closed(false),
      _docApiConverter(configUri, std::make_shared<PlaceHolderBucketResolver>())
{
    _component.registerMetricUpdateHook(*this, framework::SecondTime(5));
    _component.registerMetric(_metrics);
}

void
CommunicationManager::onOpen()
{
    _configFetcher.reset(new config::ConfigFetcher(_configUri.getContext()));
    _configFetcher->subscribe<vespa::config::content::core::StorCommunicationmanagerConfig>(_configUri.getConfigId(), this);
    _configFetcher->start();
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    _thread = _component.startThread(*this, maxProcessingTime);

    if (_listener) {
        _listener->registerHandle(_component.getIdentity());
    }
}

CommunicationManager::~CommunicationManager()
{
    if (!_closed && StorageLink::getState() >= StorageLink::OPENED) {
        // We can reach this state if onOpen fails due to network problems or
        // other exceptions. The storage link will be in an opened state,
        // but it cannot in general call onClose on a link that failed onOpen,
        // as this would violate the assumption that close should always follow
        // open. We can allow ourselves to explicitly close in the constructor
        // because our onClose handles closing a partially initialized state.
        onClose();
    }

    _sourceSession.reset();
    _messageBusSession.reset();
    _mbus.reset();

    // Clear map of sent messages _before_ we delete any visitor threads to
    // avoid any issues where unloading shared libraries causes messages
    // created by dynamic visitors to point to unmapped memory
    _messageBusSent.clear();

    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
}

void CommunicationManager::onClose()
{
    // Avoid getting config during shutdown
    _configFetcher.reset();

    _closed = true;

    if (_mbus) {
        if (_messageBusSession) {
            _messageBusSession->close();
        }
    }

    if (_listener) {
        _listener->close();
    }

    // Stopping pumper thread should stop all incoming messages from being
    // processed.
    if (_thread) {
        _thread->interrupt();
        _eventQueue.signal();
        _thread->join();
        _thread.reset();
    }

    // Emptying remaining queued messages
    std::shared_ptr<api::StorageMessage> msg;
    api::ReturnCode code(api::ReturnCode::ABORTED, "Node shutting down");
    while (_eventQueue.size() > 0) {
        assert(_eventQueue.getNext(msg, 0));
        if (!msg->getType().isReply()) {
            std::shared_ptr<api::StorageReply> reply(static_cast<api::StorageCommand&>(*msg).makeReply());
            reply->setResult(code);
            sendReply(reply);
        }
    }
}

void
CommunicationManager::configureMessageBusLimits(const CommunicationManagerConfig& cfg)
{
    const bool isDist(_component.getNodeType() == lib::NodeType::DISTRIBUTOR);
    auto& mbus(_mbus->getMessageBus());
    mbus.setMaxPendingCount(isDist ? cfg.mbusDistributorNodeMaxPendingCount
                                   : cfg.mbusContentNodeMaxPendingCount);
    mbus.setMaxPendingSize(isDist ? cfg.mbusDistributorNodeMaxPendingSize
                                  : cfg.mbusContentNodeMaxPendingSize);
}

void CommunicationManager::configure(std::unique_ptr<CommunicationManagerConfig> config)
{
    // Only allow dynamic (live) reconfiguration of message bus limits.
    if (_mbus.get()) {
        configureMessageBusLimits(*config);
        if (_mbus->getRPCNetwork().getPort() != config->mbusport) {
            auto m = make_string("mbus port changed from %d to %d. Will conduct a quick, but controlled restart.",
                                 _mbus->getRPCNetwork().getPort(), config->mbusport);
            LOG(warning, "%s", m.c_str());
            _component.requestShutdown(m);
        }
        if (_listener->getListenPort() != config->rpcport) {
            auto m = make_string("rpc port changed from %d to %d. Will conduct a quick, but controlled restart.",
                                 _listener->getListenPort(), config->rpcport);
            LOG(warning, "%s", m.c_str());
            _component.requestShutdown(m);
        }
        return;
    };

    if (!_configUri.empty()) {
        mbus::RPCNetworkParams params;
        LOG(debug, "setting up slobrok config from id: '%s", _configUri.getConfigId().c_str());

        params.setSlobrokConfig(_configUri);

        params.setIdentity(mbus::Identity(_component.getIdentity()));
        if (config->mbusport != -1) {
            params.setListenPort(config->mbusport);
        }

        using CompressionConfig = vespalib::compression::CompressionConfig;
        CompressionConfig::Type compressionType = CompressionConfig::toType(
                CommunicationManagerConfig::Mbus::Compress::getTypeName(config->mbus.compress.type).c_str());
        params.setCompressionConfig(CompressionConfig(compressionType, config->mbus.compress.level,
                                                      90, config->mbus.compress.limit));
        // Configure messagebus here as we for legacy reasons have
        // config here.
        _mbus = std::make_unique<mbus::RPCMessageBus>(
                mbus::ProtocolSet()
                        .add(std::make_shared<documentapi::DocumentProtocol>(*_component.getLoadTypes(), _component.getTypeRepo()))
                        .add(std::make_shared<mbusprot::StorageProtocol>(_component.getTypeRepo(), *_component.getLoadTypes(),
                                                                         _component.enableMultipleBucketSpaces())),
                params,
                _configUri);

        configureMessageBusLimits(*config);
    }

    _listener.reset(new FNetListener(*this, _configUri, config->rpcport));

    if (_mbus.get()) {
        mbus::DestinationSessionParams dstParams;
        dstParams.setName("default");
        dstParams.setBroadcastName(true);
        dstParams.setMessageHandler(*this);
        _messageBusSession = _mbus->getMessageBus().createDestinationSession(dstParams);

        mbus::SourceSessionParams srcParams;
        srcParams.setThrottlePolicy(mbus::IThrottlePolicy::SP());
        srcParams.setReplyHandler(*this);
        _sourceSession = _mbus->getMessageBus().createSourceSession(srcParams);
    }
}

void
CommunicationManager::process(const std::shared_ptr<api::StorageMessage>& msg)
{
    MBUS_TRACE(msg->getTrace(), 9, "Communication manager: Sending message down chain.");
    framework::MilliSecTimer startTime(_component.getClock());
    try {
        LOG(spam, "Process: %s", msg->toString().c_str());

        if (!onDown(msg)) {
            sendDown(msg);
        }

        LOG(spam, "Done processing: %s", msg->toString().c_str());
        _metrics.messageProcessTime[msg->getLoadType()].addValue(startTime.getElapsedTimeAsDouble());
    } catch (std::exception& e) {
        LOGBP(error, "When running command %s, caught exception %s. Discarding message",
              msg->toString().c_str(), e.what());
        _metrics.exceptionMessageProcessTime[msg->getLoadType()].addValue(startTime.getElapsedTimeAsDouble());
    } catch (...) {
        LOG(fatal, "Caught fatal exception in communication manager");
        throw;
    }
}

void
CommunicationManager::enqueue(std::shared_ptr<api::StorageMessage> msg)
{
    assert(msg);
    LOG(spam, "Enq storage message %s, priority %d", msg->toString().c_str(), msg->getPriority());
    _eventQueue.enqueue(std::move(msg));
}

bool
CommunicationManager::onUp(const std::shared_ptr<api::StorageMessage> & msg)
{
    MBUS_TRACE(msg->getTrace(), 6, "Communication manager: Sending " + msg->toString());
    if (msg->getType().isReply()) {
        const api::StorageReply & m = static_cast<const api::StorageReply&>(*msg);
        if (m.getResult().failed()) {
            LOG(debug, "Request %s failed: %s", msg->getType().toString().c_str(), m.getResult().toString().c_str());
        }
        return sendReply(std::static_pointer_cast<api::StorageReply>(msg));
    } else {
        return sendCommand(std::static_pointer_cast<api::StorageCommand>(msg));
    }
}

void
CommunicationManager::sendMessageBusMessage(const std::shared_ptr<api::StorageCommand>& msg,
                                            std::unique_ptr<mbus::Message> mbusMsg,
                                            const mbus::Route& route)
{
    // Relaxed load since we're not doing any dependent reads that aren't
    // already covered by some other form of explicit synchronization.
    if (_closed.load(std::memory_order_relaxed)) {
        return;
    }

    LOG(spam, "Sending message bus msg of type %d", mbusMsg->getType());

    MBUS_TRACE(mbusMsg->getTrace(), 6, "Communication manager: Passing message to source session");
    mbus::Result result = _sourceSession->send(std::move(mbusMsg), route);

    if (!result.isAccepted()) {
        std::shared_ptr<api::StorageReply> reply(msg->makeReply());
        if (reply.get()) {
            if (result.getError().getCode() > mbus::ErrorCode::FATAL_ERROR) {
                reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, result.getError().getMessage()));
            } else {
                reply->setResult(api::ReturnCode(api::ReturnCode::BUSY, result.getError().getMessage()));
            }
        } else {
            LOG(spam, "Failed to synthesize reply");
        }

        sendDown(reply);
    }
}

bool
CommunicationManager::sendCommand(
        const std::shared_ptr<api::StorageCommand> & msg)
{
    if (!msg->getAddress()) {
        LOGBP(warning, "Got command without address of type %s in CommunicationManager::sendCommand",
              msg->getType().getName().c_str());
        return false;
    }
    if (!msg->sourceIndexSet()) {
        msg->setSourceIndex(_component.getIndex());
    }
    // Components can not specify what storage node to send to
    // without specifying protocol. This is a workaround, such that code
    // doesn't have to care whether message is in documentapi or storage
    // protocol.
    api::StorageMessageAddress address(*msg->getAddress());
    switch (msg->getType().getId()) {
        case api::MessageType::STATBUCKET_ID: {
            if (address.getProtocol() == api::StorageMessageAddress::STORAGE) {
                address.setProtocol(api::StorageMessageAddress::DOCUMENT);
            }
        }
        default:
            break;
    }

    framework::MilliSecTimer startTime(_component.getClock());
    switch (address.getProtocol()) {
    case api::StorageMessageAddress::STORAGE:
    {
        LOG(spam, "Send to %s: %s", address.toString().c_str(), msg->toString().c_str());

        std::unique_ptr<mbus::Message> cmd(new mbusprot::StorageCommand(msg));

        cmd->setContext(mbus::Context(msg->getMsgId()));
        cmd->setRetryEnabled(address.retryEnabled());
        cmd->setTimeRemaining(msg->getTimeout());
        cmd->setTrace(msg->getTrace());
        sendMessageBusMessage(msg, std::move(cmd), address.getRoute());
        break;
    }
    case api::StorageMessageAddress::DOCUMENT:
    {
        MBUS_TRACE(msg->getTrace(), 7, "Communication manager: Converting storageapi message to documentapi");

        std::unique_ptr<mbus::Message> mbusMsg(_docApiConverter.toDocumentAPI(*msg, _component.getTypeRepo()));

        if (mbusMsg.get()) {
            MBUS_TRACE(msg->getTrace(), 7, "Communication manager: Converted OK");
            mbusMsg->setTrace(msg->getTrace());
            mbusMsg->setRetryEnabled(address.retryEnabled());

            {
                vespalib::LockGuard lock(_messageBusSentLock);
                _messageBusSent[msg->getMsgId()] = msg;
            }
            sendMessageBusMessage(msg, std::move(mbusMsg), address.getRoute());
            break;
        } else {
            LOGBM(warning, "This type of message can't be sent via messagebus");
            return false;
        }
    }
    default:
        return false;
    }
    _metrics.sendCommandLatency.addValue(startTime.getElapsedTimeAsDouble());
    return true;
}

void
CommunicationManager::serializeNodeState(const api::GetNodeStateReply& gns, std::ostream& os,
                                         bool includeDescription, bool includeDiskDescription, bool useOldFormat) const
{
    vespalib::asciistream tmp;
    if (gns.hasNodeState()) {
        gns.getNodeState().serialize(tmp, "", includeDescription, includeDiskDescription, useOldFormat);
    } else {
        _component.getStateUpdater().getReportedNodeState()->serialize(tmp, "", includeDescription,
                                                                       includeDiskDescription, useOldFormat);
    }
    os << tmp.str();
}

void
CommunicationManager::sendDirectRPCReply(
        RPCRequestWrapper& request,
        const std::shared_ptr<api::StorageReply>& reply)
{
    std::string requestName(request.getMethodName());
    if (requestName == "getnodestate3") {
        api::GetNodeStateReply& gns(static_cast<api::GetNodeStateReply&>(*reply));
        std::ostringstream ns;
        serializeNodeState(gns, ns, true, true, false);
        request.addReturnString(ns.str().c_str());
        request.addReturnString(gns.getNodeInfo().c_str());
        LOGBP(debug, "Sending getnodestate3 reply with host info '%s'.", gns.getNodeInfo().c_str());
    } else if (requestName == "getnodestate2") {
        api::GetNodeStateReply& gns(static_cast<api::GetNodeStateReply&>(*reply));
        std::ostringstream ns;
        serializeNodeState(gns, ns, true, true, false);
        request.addReturnString(ns.str().c_str());
        LOGBP(debug, "Sending getnodestate2 reply with no host info.");
    } else if (requestName == "setsystemstate2") {
        // No data to return
    } else {
        request.addReturnInt(reply->getResult().getResult());
        request.addReturnString(reply->getResult().getMessage().c_str());

        if (reply->getType() == api::MessageType::GETNODESTATE_REPLY) {
            api::GetNodeStateReply& gns(static_cast<api::GetNodeStateReply&>(*reply));
            std::ostringstream ns;
            serializeNodeState(gns, ns, false, false, true);
            request.addReturnString(ns.str().c_str());
            request.addReturnInt(static_cast<int>(gns.getNodeState().getInitProgress().getValue() * 100));
        }
    }

    request.returnRequest();
}

void
CommunicationManager::sendMessageBusReply(
        StorageTransportContext& context,
        const std::shared_ptr<api::StorageReply>& reply)
{
    // Using messagebus for communication.
    mbus::Reply::UP replyUP;

    LOG(spam, "Sending message bus reply %s", reply->toString().c_str());

    // If this was originally documentapi, create a reply now and transfer the
    // state.
    if (context._docAPIMsg.get()) {
        if (reply->getResult().getResult() == api::ReturnCode::WRONG_DISTRIBUTION) {
            replyUP.reset(new documentapi::WrongDistributionReply(reply->getResult().getMessage()));
            replyUP->swapState(*context._docAPIMsg);
            replyUP->setTrace(reply->getTrace());
            replyUP->addError(mbus::Error(documentapi::DocumentProtocol::ERROR_WRONG_DISTRIBUTION,
                                          reply->getResult().getMessage()));
        } else {
            replyUP = context._docAPIMsg->createReply();
            replyUP->swapState(*context._docAPIMsg);
            replyUP->setTrace(reply->getTrace());
            replyUP->setMessage(std::move(context._docAPIMsg));
            _docApiConverter.transferReplyState(*reply, *replyUP);
        }
    } else if (context._storageProtocolMsg.get()) {
        replyUP.reset(new mbusprot::StorageReply(reply));
        if (reply->getResult().getResult() != api::ReturnCode::OK) {
            replyUP->addError(mbus::Error(reply->getResult().getResult(), reply->getResult().getMessage()));
        }

        replyUP->swapState(*context._storageProtocolMsg);
        replyUP->setTrace(reply->getTrace());
        replyUP->setMessage(std::move(context._storageProtocolMsg));
    }

    if (replyUP.get() != NULL) {
        // Forward message only if it was successfully stored in storage.
        if (!replyUP->hasErrors()) {
            mbus::Message::UP messageUP = replyUP->getMessage();

            if (messageUP.get() && messageUP->getRoute().hasHops()) {
                messageUP->setContext(mbus::Context(FORWARDED_MESSAGE));
                _sourceSession->send(std::move(messageUP));
            }
        }

        _messageBusSession->reply(std::move(replyUP));
    }
}

bool
CommunicationManager::sendReply(
        const std::shared_ptr<api::StorageReply>& reply)
{
    // Relaxed load since we're not doing any dependent reads that aren't
    // already covered by some other form of explicit synchronization.
    if (_closed.load(std::memory_order_relaxed)) {
        reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Node is shutting down"));
    }

    std::unique_ptr<StorageTransportContext> context(static_cast<StorageTransportContext*>(reply->getTransportContext().release()));

    if (!context.get()) {
        LOG(spam, "No transport context in reply %s", reply->toString().c_str());
        return false;
    }

    framework::MilliSecTimer startTime(_component.getClock());
    if (context->_request.get()) {
        sendDirectRPCReply(*(context->_request.get()), reply);
    } else {
        sendMessageBusReply(*context, reply);
    }
    _metrics.sendReplyLatency.addValue(startTime.getElapsedTimeAsDouble());
    return true;
}


void
CommunicationManager::run(framework::ThreadHandle& thread)
{
    while (!thread.interrupted()) {
        thread.registerTick();
        std::shared_ptr<api::StorageMessage> msg;
        if (_eventQueue.getNext(msg, 100)) {
            process(msg);
        }
        std::lock_guard<std::mutex> guard(_earlierGenerationsLock);
        for (EarlierProtocols::iterator it(_earlierGenerations.begin());
             !_earlierGenerations.empty() &&
             ((it->first + TEN_MINUTES) < _component.getClock().getTimeInSeconds());
             it = _earlierGenerations.begin())
        {
            _earlierGenerations.erase(it);
        }
    }
}

void
CommunicationManager::updateMetrics(const MetricLockGuard &)
{
    _metrics.queueSize.addValue(_eventQueue.size());
}

void
CommunicationManager::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "CommunicationManager";
}

void CommunicationManager::updateMessagebusProtocol(
        const document::DocumentTypeRepo::SP &repo) {
    if (_mbus.get()) {
        framework::SecondTime now(_component.getClock().getTimeInSeconds());
        auto newDocumentProtocol = std::make_shared<documentapi::DocumentProtocol>(*_component.getLoadTypes(), repo);
        std::lock_guard<std::mutex> guard(_earlierGenerationsLock);
        _earlierGenerations.push_back(std::make_pair(now, _mbus->getMessageBus().putProtocol(newDocumentProtocol)));
        mbus::IProtocol::SP newStorageProtocol(new mbusprot::StorageProtocol(repo, *_component.getLoadTypes(), _component.enableMultipleBucketSpaces()));
        _earlierGenerations.push_back(std::make_pair(now, _mbus->getMessageBus().putProtocol(newStorageProtocol)));
    }
}

void CommunicationManager::updateBucketSpacesConfig(const BucketspacesConfig& config) {
    _docApiConverter.setBucketResolver(ConfigurableBucketResolver::from_config(config));
}

} // storage
