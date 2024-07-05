// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "communicationmanager.h"
#include "rpcrequestwrapper.h"
#include <vespa/documentapi/messagebus/messages/wrongdistributionreply.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storage/common/bucket_resolver.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/storageserver/configurable_bucket_resolver.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/cluster_controller_api_rpc_service.h>
#include <vespa/storage/storageserver/rpc/message_codec_provider.h>
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <string_view>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".communication.manager");

using vespalib::make_string;
using document::FixedBucketSpaces;

namespace storage {

StorageTransportContext::StorageTransportContext(std::unique_ptr<documentapi::DocumentMessage> msg)
    : _docAPIMsg(std::move(msg))
{ }

StorageTransportContext::StorageTransportContext(std::unique_ptr<RPCRequestWrapper> request)
    : _request(std::move(request))
{ }

StorageTransportContext::~StorageTransportContext() = default;

void
CommunicationManager::receiveStorageReply(const std::shared_ptr<api::StorageReply>& reply)
{
    assert(reply);
    process(reply);
}

namespace {

vespalib::string getNodeId(StorageComponent& sc) {
    vespalib::asciistream ost;
    ost << sc.cluster_context().cluster_name() << "/" << sc.getNodeType() << "/" << sc.getIndex();
    return ost.str();
}

constexpr vespalib::duration STALE_PROTOCOL_LIFETIME = 1h;

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
        auto reply = std::make_unique<mbus::EmptyReply>();
        reply->addError(mbus::Error(documentapi::DocumentProtocol::ERROR_ABORTED, "Node shutting down"));
        msg->swapState(*reply);
        _messageBusSession->reply(std::move(reply));
        return;
    }
    const vespalib::string & protocolName = msg->getProtocol();

    if (protocolName == documentapi::DocumentProtocol::NAME) {
        std::unique_ptr<documentapi::DocumentMessage> docMsgPtr(static_cast<documentapi::DocumentMessage*>(msg.release()));

        assert(docMsgPtr);

        std::unique_ptr<api::StorageCommand> cmd;
        try {
            cmd = _docApiConverter.toStorageAPI(static_cast<documentapi::DocumentMessage&>(*docMsgPtr));
        } catch (document::UnknownBucketSpaceException& e) {
            fail_with_unresolvable_bucket_space(std::move(docMsgPtr), e.getMessage());
            return;
        }

        if ( ! cmd) {
            LOGBM(warning, "Unsupported message: StorageApi could not convert message of type %d to a storageapi message",
                  docMsgPtr->getType());
            _metrics.convertToStorageAPIFailures.inc();
            return;
        }

        cmd->setTrace(docMsgPtr->steal_trace());
        cmd->setTransportContext(std::make_unique<StorageTransportContext>(std::move(docMsgPtr)));

        process(std::move(cmd));
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

        if (message) {
            std::unique_ptr<mbus::Reply> convertedReply;

            const vespalib::string& protocolName = message->getProtocol();
            if (protocolName == documentapi::DocumentProtocol::NAME) {
                convertedReply = static_cast<documentapi::DocumentMessage &>(*message).createReply();
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
                std::lock_guard lock(_messageBusSentLock);
                auto iter(_messageBusSent.find(reply->getContext().value.UINT64));
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

            if (sar) {
                sar->setTrace(reply->steal_trace());
                receiveStorageReply(sar);
            }
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
    std::unique_ptr<mbus::Reply> reply;
    reply = std::make_unique<mbus::EmptyReply>();
    reply->addError(mbus::Error(documentapi::DocumentProtocol::ERROR_REJECTED, error_message));
    msg->swapState(*reply);
    _metrics.bucketSpaceMappingFailures.inc();
    _messageBusSession->reply(std::move(reply));
}

namespace {

struct PlaceHolderBucketResolver : public BucketResolver {
    [[nodiscard]] document::Bucket bucketFromId(const document::DocumentId &) const override {
        return {FixedBucketSpaces::default_space(), document::BucketId(0)};
    }
    [[nodiscard]] document::BucketSpace bucketSpaceFromName(const vespalib::string &) const override {
        return FixedBucketSpaces::default_space();
    }
    [[nodiscard]] vespalib::string nameFromBucketSpace(const document::BucketSpace &bucketSpace) const override {
        assert(bucketSpace == FixedBucketSpaces::default_space());
        return FixedBucketSpaces::to_string(bucketSpace);
    }
};

vespalib::compression::CompressionConfig
convert_to_rpc_compression_config(const vespa::config::content::core::StorCommunicationmanagerConfig& mgr_config) {
    using vespalib::compression::CompressionConfig;
    using vespa::config::content::core::StorCommunicationmanagerConfig;
    auto compression_type = CompressionConfig::toType(
            StorCommunicationmanagerConfig::Rpc::Compress::getTypeName(mgr_config.rpc.compress.type).c_str());
    return CompressionConfig(compression_type, mgr_config.rpc.compress.level, 90, mgr_config.rpc.compress.limit);
}

}

CommunicationManager::CommunicationManager(StorageComponentRegister& compReg,
                                           const config::ConfigUri& configUri,
                                           const CommunicationManagerConfig& bootstrap_config)
    : StorageLink("Communication manager", MsgDownOnFlush::Allowed, MsgUpOnClosed::Disallowed),
      _component(compReg, "communicationmanager"),
      _metrics(),
      _shared_rpc_resources(),    // Created upon initial configuration
      _storage_api_rpc_service(), // (ditto)
      _cc_rpc_service(),          // (ditto)
      _eventQueue(),
      _bootstrap_config(std::make_unique<CommunicationManagerConfig>(bootstrap_config)),
      _mbus(),
      _configUri(configUri),
      _closed(false),
      _docApiConverter(std::make_shared<PlaceHolderBucketResolver>()),
      _thread()
{
    _component.registerMetricUpdateHook(*this, 5s);
    _component.registerMetric(_metrics);
}

void
CommunicationManager::onOpen()
{
    // We have to hold on to the bootstrap config until we reach the open-phase, as the
    // actual RPC/mbus endpoints are started at the first config edge.
    // Note: this is called as part of synchronous node initialization, which explicitly
    // prevents any concurrent reconfiguration prior to opening all storage chain components,
    // i.e. there's no risk of on_configure() being called _prior_ to us getting here.
    on_configure(*_bootstrap_config);
    _bootstrap_config.reset();
    _thread = _component.startThread(*this, 60s);

    if (_shared_rpc_resources) {
        _shared_rpc_resources->start_server_and_register_slobrok(_component.getIdentity());
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

void
CommunicationManager::onClose()
{
    _closed.store(true, std::memory_order_seq_cst);
    if (_cc_rpc_service) {
        _cc_rpc_service->close(); // Auto-abort all incoming CC RPC requests from now on
    }
    // Sync all RPC threads to ensure that any subsequent RPCs must observe the closed-flags we just set
    if (_shared_rpc_resources) {
        _shared_rpc_resources->sync_all_threads();
    }

    if (_mbus && _messageBusSession) {
        // Closing the mbus session unregisters the destination session and syncs the worker
        // thread(s), so once this call returns we should not observe further incoming requests
        // through this pipeline. Previous messages may already be in flight internally; these
        // will be handled by flushing-phases.
        _messageBusSession->close();
    }

    // Stopping internal message dispatch thread should stop all incoming _async_ messages
    // from being processed. _Synchronously_ dispatched RPCs are still passing through.
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
        assert(_eventQueue.getNext(msg, 0ms));
        if (!msg->getType().isReply()) {
            std::shared_ptr<api::StorageReply> reply(dynamic_cast<api::StorageCommand&>(*msg).makeReply());
            reply->setResult(code);
            sendReply(reply);
        }
    }
}

void
CommunicationManager::onFlush(bool downwards)
{
    if (downwards) {
        // Sync RPC threads once more (with feeling!) to ensure that any closing done by other components
        // during the storage chain onClose() is visible to these.
        if (_shared_rpc_resources) {
            _shared_rpc_resources->sync_all_threads();
        }
        // By this point, no inbound RPCs (requests and responses) should be allowed any further down
        // than the Bouncer component, where they will be, well, bounced.
    } else {
        // All components further down the storage chain should now be completely closed
        // and flushed, and all message-dispatching threads should have been shut down.
        // It's possible that the RPC threads are still butting heads up against the Bouncer
        // component, so we conclude the shutdown ceremony by taking down the RPC subsystem.
        // This transitively waits for all RPC threads to complete.
        if (_shared_rpc_resources) {
            _shared_rpc_resources->shutdown();
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

void
CommunicationManager::on_configure(const CommunicationManagerConfig& config)
{
    // Only allow dynamic (live) reconfiguration of message bus limits.
    if (_mbus) {
        configureMessageBusLimits(config);
        if (_mbus->getRPCNetwork().getPort() != config.mbusport) {
            auto m = make_string("mbus port changed from %d to %d. Will conduct a quick, but controlled restart.",
                                 _mbus->getRPCNetwork().getPort(), config.mbusport);
            LOG(warning, "%s", m.c_str());
            _component.requestShutdown(m);
        }
        if (_shared_rpc_resources->listen_port() != config.rpcport) {
            auto m = make_string("rpc port changed from %d to %d. Will conduct a quick, but controlled restart.",
                                 _shared_rpc_resources->listen_port(), config.rpcport);
            LOG(warning, "%s", m.c_str());
            _component.requestShutdown(m);
        }
        return;
    };

    if (!_configUri.empty()) {
        LOG(debug, "setting up slobrok config from id: '%s", _configUri.getConfigId().c_str());
        mbus::RPCNetworkParams params(_configUri);
        params.setConnectionExpireSecs(config.mbus.rpctargetcache.ttl);
        params.setNumNetworkThreads(std::max(1, config.mbus.numNetworkThreads));
        params.setNumRpcTargets(std::max(1, config.mbus.numRpcTargets));
        params.events_before_wakeup(std::max(1, config.mbus.eventsBeforeWakeup));
        params.setTcpNoDelay(config.mbus.tcpNoDelay);
        params.required_capabilities(vespalib::net::tls::CapabilitySet::of({
            vespalib::net::tls::Capability::content_document_api()
        }));

        params.setIdentity(mbus::Identity(_component.getIdentity()));
        if (config.mbusport != -1) {
            params.setListenPort(config.mbusport);
        }

        using CompressionConfig = vespalib::compression::CompressionConfig;
        CompressionConfig::Type compressionType = CompressionConfig::toType(
                CommunicationManagerConfig::Mbus::Compress::getTypeName(config.mbus.compress.type).c_str());
        params.setCompressionConfig(CompressionConfig(compressionType, config.mbus.compress.level,
                                                      90, config.mbus.compress.limit));

        // Configure messagebus here as we for legacy reasons have
        // config here.
        auto documentTypeRepo = _component.getTypeRepo()->documentTypeRepo;
        _mbus = std::make_unique<mbus::RPCMessageBus>(
                mbus::ProtocolSet().add(std::make_shared<documentapi::DocumentProtocol>(documentTypeRepo)),
                params,
                _configUri);

        configureMessageBusLimits(config);
    }

    _message_codec_provider = std::make_unique<rpc::MessageCodecProvider>(_component.getTypeRepo()->documentTypeRepo);
    _shared_rpc_resources = std::make_unique<rpc::SharedRpcResources>(_configUri, config.rpcport,
                                                                      config.rpc.numNetworkThreads, config.rpc.eventsBeforeWakeup);
    _cc_rpc_service = std::make_unique<rpc::ClusterControllerApiRpcService>(*this, *_shared_rpc_resources);
    rpc::StorageApiRpcService::Params rpc_params;
    rpc_params.compression_config = convert_to_rpc_compression_config(config);
    rpc_params.num_rpc_targets_per_node = config.rpc.numTargetsPerNode;
    _storage_api_rpc_service = std::make_unique<rpc::StorageApiRpcService>(
            *this, *_shared_rpc_resources, *_message_codec_provider, rpc_params);

    if (_mbus) {
        mbus::DestinationSessionParams dstParams;
        dstParams.setName("default");
        dstParams.setBroadcastName(true);
        dstParams.defer_registration(true); // Deferred session registration; see rationale below
        dstParams.setMessageHandler(*this);
        _messageBusSession = _mbus->getMessageBus().createDestinationSession(dstParams);

        mbus::SourceSessionParams srcParams;
        srcParams.setThrottlePolicy(mbus::IThrottlePolicy::SP());
        srcParams.setReplyHandler(*this);
        _sourceSession = _mbus->getMessageBus().createSourceSession(srcParams);

        // Creating a DestinationSession that is immediately registered as available for business
        // means we may theoretically start receiving messages over the session even before the call returns
        // to the caller. Either way there would be no memory barrier that ensures that _messageBusSession
        // would be fully visible to the MessageBus threads (since it's written after return).
        // To avoid this sneaky scenario, defer registration (and thus introduce a barrier) until
        // _after_ we've initialized our internal member variables.
        _messageBusSession->register_session_deferred();
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
        _metrics.messageProcessTime.addValue(startTime.getElapsedTimeAsDouble());
    } catch (std::exception& e) {
        LOGBP(error, "When running command %s, caught exception %s. Discarding message",
              msg->toString().c_str(), e.what());
        _metrics.exceptionMessageProcessTime.addValue(startTime.getElapsedTimeAsDouble());
    }
}

// Called directly by RPC threads
void CommunicationManager::dispatch_sync(std::shared_ptr<api::StorageMessage> msg) {
    LOG(spam, "Direct dispatch of storage message %s, priority %d", msg->toString().c_str(), msg->getPriority());
    // If process is shutting down, msg will be synchronously aborted by the Bouncer component
    process(msg);
}

// Called directly by RPC threads (for incoming CC requests) and by any other request-dispatching
// threads (i.e. calling sendUp) when address resolution fails and an internal error response is generated.
void CommunicationManager::dispatch_async(std::shared_ptr<api::StorageMessage> msg) {
    LOG(spam, "Enqueued dispatch of storage message %s, priority %d", msg->toString().c_str(), msg->getPriority());
    _eventQueue.enqueue(std::move(msg));
}

bool
CommunicationManager::onUp(const std::shared_ptr<api::StorageMessage> & msg)
{
    MBUS_TRACE(msg->getTrace(), 6, "Communication manager: Sending " + msg->toString());
    if (msg->getType().isReply()) {
        const auto & m = static_cast<const api::StorageReply&>(*msg);
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
        if (reply) {
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
CommunicationManager::sendCommand(const std::shared_ptr<api::StorageCommand> & msg)
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
            if (address.getProtocol() == api::StorageMessageAddress::Protocol::STORAGE) {
                address.setProtocol(api::StorageMessageAddress::Protocol::DOCUMENT);
            }
        }
        default:
            break;
    }

    framework::MilliSecTimer startTime(_component.getClock());
    switch (address.getProtocol()) {
    case api::StorageMessageAddress::Protocol::STORAGE:
    {
        LOG(debug, "Send to %s: %s", address.toString().c_str(), msg->toString().c_str());
        _storage_api_rpc_service->send_rpc_v1_request(msg);
        break;
    }
    case api::StorageMessageAddress::Protocol::DOCUMENT:
    {
        MBUS_TRACE(msg->getTrace(), 7, "Communication manager: Converting storageapi message to documentapi");

        std::unique_ptr<mbus::Message> mbusMsg(_docApiConverter.toDocumentAPI(*msg));

        if (mbusMsg) {
            MBUS_TRACE(msg->getTrace(), 7, "Communication manager: Converted OK");
            mbusMsg->setTrace(msg->steal_trace());
            mbusMsg->setRetryEnabled(false);

            {
                std::lock_guard lock(_messageBusSentLock);
                _messageBusSent[msg->getMsgId()] = msg;
            }
            sendMessageBusMessage(msg, std::move(mbusMsg), address.to_mbus_route());
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
CommunicationManager::serializeNodeState(const api::GetNodeStateReply& gns, std::ostream& os, bool includeDescription) const
{
    vespalib::asciistream tmp;
    if (gns.hasNodeState()) {
        gns.getNodeState().serialize(tmp, "", includeDescription);
    } else {
        _component.getStateUpdater().getReportedNodeState()->serialize(tmp, "", includeDescription);
    }
    os << tmp.str();
}

void
CommunicationManager::sendDirectRPCReply(RPCRequestWrapper& request,
                                         const std::shared_ptr<api::StorageReply>& reply)
{
    std::string_view requestName(request.getMethodName()); // TODO non-name based dispatch
    // TODO rework this entire dispatch mechanism :D
    if (requestName == rpc::StorageApiRpcService::rpc_v1_method_name()) {
        _storage_api_rpc_service->encode_rpc_v1_response(*request.raw_request(), *reply);
    } else if (requestName == "getnodestate3") {
        auto& gns(dynamic_cast<api::GetNodeStateReply&>(*reply));
        std::ostringstream ns;
        serializeNodeState(gns, ns, true);
        request.addReturnString(ns.str().c_str());
        request.addReturnString(gns.getNodeInfo().c_str());
        LOGBP(debug, "Sending getnodestate3 reply with host info '%s'.", gns.getNodeInfo().c_str());
    } else if (requestName == "getnodestate2") {
        auto& gns(dynamic_cast<api::GetNodeStateReply&>(*reply));
        std::ostringstream ns;
        serializeNodeState(gns, ns, true);
        request.addReturnString(ns.str().c_str());
        LOGBP(debug, "Sending getnodestate2 reply with no host info.");
    } else if (requestName == "setsystemstate2" || requestName == "setdistributionstates") {
        // No data to return, but the request must be failed iff we rejected the state version
        // due to a higher version having been previously received.
        auto& state_reply = dynamic_cast<api::SetSystemStateReply&>(*reply);
        if (state_reply.getResult().getResult() == api::ReturnCode::REJECTED) {
            vespalib::string err_msg = state_reply.getResult().getMessage(); // ReturnCode message is string_view
            request.returnError(FRTE_RPC_METHOD_FAILED, err_msg.c_str());
            return;
        }
    } else if (requestName == "activate_cluster_state_version") {
        auto& activate_reply(dynamic_cast<api::ActivateClusterStateVersionReply&>(*reply));
        request.addReturnInt(activate_reply.actualVersion());
        LOGBP(debug, "sending activate_cluster_state_version reply for version %u with actual version %u ",
                     activate_reply.activateVersion(), activate_reply.actualVersion());
    } else {
        request.addReturnInt(reply->getResult().getResult());
        std::string_view m = reply->getResult().getMessage();
        request.addReturnString(m.data(), m.size());

        if (reply->getType() == api::MessageType::GETNODESTATE_REPLY) {
            auto& gns(static_cast<api::GetNodeStateReply&>(*reply));
            std::ostringstream ns;
            serializeNodeState(gns, ns, false);
            request.addReturnString(ns.str().c_str());
            request.addReturnInt(static_cast<int>(gns.getNodeState().getInitProgress().getValue() * 100));
        }
    }

    request.returnRequest();
}

void
CommunicationManager::sendMessageBusReply(StorageTransportContext& context,
                                          const std::shared_ptr<api::StorageReply>& reply)
{
    // Using messagebus for communication.
    mbus::Reply::UP replyUP;

    LOG(spam, "Sending message bus reply %s", reply->toString().c_str());
    assert(context._docAPIMsg); // StorageProtocol no longer uses MessageBus carrier.

    // Create an MBus reply and transfer state to it.
    if (reply->getResult().getResult() == api::ReturnCode::WRONG_DISTRIBUTION) {
        replyUP = std::make_unique<documentapi::WrongDistributionReply>(reply->getResult().getMessage());
        replyUP->swapState(*context._docAPIMsg);
        replyUP->setTrace(reply->steal_trace());
        replyUP->addError(mbus::Error(documentapi::DocumentProtocol::ERROR_WRONG_DISTRIBUTION,
                                      reply->getResult().getMessage()));
    } else {
        replyUP = context._docAPIMsg->createReply();
        replyUP->swapState(*context._docAPIMsg);
        replyUP->setTrace(reply->steal_trace());
        replyUP->setMessage(std::move(context._docAPIMsg));
        _docApiConverter.transferReplyState(*reply, *replyUP);
    }

    if (!replyUP->hasErrors()) {
        mbus::Message::UP messageUP = replyUP->getMessage();

        if (messageUP && messageUP->getRoute().hasHops()) {
            messageUP->setContext(mbus::Context(FORWARDED_MESSAGE));
            _sourceSession->send(std::move(messageUP));
        }
    }
    _messageBusSession->reply(std::move(replyUP));
}

bool
CommunicationManager::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    // Relaxed load since we're not doing any dependent reads that aren't
    // already covered by some other form of explicit synchronization.
    if (_closed.load(std::memory_order_relaxed)) {
        reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Node is shutting down"));
    }

    std::unique_ptr<StorageTransportContext> context(static_cast<StorageTransportContext*>(reply->getTransportContext().release()));

    if (!context) {
        LOG(spam, "No transport context in reply %s", reply->toString().c_str());
        // If it's an autogenerated reply for an internal message type, just throw it away
        // by returning that we've handled it. No one else will handle the reply, the
        // alternative is that it ends up as warning noise in the log.
        return (reply->getType().getId() == api::MessageType::INTERNAL_REPLY_ID);
    }

    framework::MilliSecTimer startTime(_component.getClock());
    if (context->_request) {
        sendDirectRPCReply(*(context->_request), reply);
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
        thread.registerTick(framework::UNKNOWN_CYCLE);
        std::shared_ptr<api::StorageMessage> msg;
        if (_eventQueue.getNext(msg, 100ms)) {
            process(msg);
        }
        std::lock_guard<std::mutex> guard(_earlierGenerationsLock);
        for (auto it(_earlierGenerations.begin());
             !_earlierGenerations.empty() &&
             ((it->first + STALE_PROTOCOL_LIFETIME) < _component.getClock().getMonotonicTime());
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
CommunicationManager::print(std::ostream& out, bool , const std::string& ) const
{
    out << "CommunicationManager";
}

void CommunicationManager::updateMessagebusProtocol(const std::shared_ptr<const document::DocumentTypeRepo>& repo) {
    if (_mbus) {
        vespalib::steady_time now(_component.getClock().getMonotonicTime());
        auto newDocumentProtocol = std::make_shared<documentapi::DocumentProtocol>(repo);
        std::lock_guard<std::mutex> guard(_earlierGenerationsLock);
        _earlierGenerations.emplace_back(now, _mbus->getMessageBus().putProtocol(newDocumentProtocol));
    }
    if (_message_codec_provider) {
        _message_codec_provider->update_atomically(repo);
    }
}

void CommunicationManager::updateBucketSpacesConfig(const BucketspacesConfig& config) {
    _docApiConverter.setBucketResolver(ConfigurableBucketResolver::from_config(config));
}

bool
CommunicationManager::address_visible_in_slobrok(const api::StorageMessageAddress& addr) const noexcept
{
    assert(_storage_api_rpc_service);
    return _storage_api_rpc_service->address_visible_in_slobrok_uncached(addr);
}

} // storage
