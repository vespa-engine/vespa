// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class CommunicationManager
 * @ingroup storageserver
 *
 * @brief Class used for sending messages over the network.
 *
 * @version $Id$
 */

#pragma once

#include "communicationmanagermetrics.h"
#include "documentapiconverter.h"
#include "message_dispatcher.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/config/config-stor-communicationmanager.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageapi/mbusprot/storagecommand.h>
#include <vespa/storageapi/mbusprot/storagereply.h>
#include <vespa/messagebus/imessagehandler.h>
#include <vespa/messagebus/ireplyhandler.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config-bucketspaces.h>
#include <map>
#include <queue>
#include <atomic>
#include <mutex>

namespace config {
    class ConfigFetcher;
}
namespace mbus {
    class RPCMessageBus;
    class SourceSession;
    class DestinationSession;
}
namespace storage {

namespace rpc {
class ClusterControllerApiRpcService;
class MessageCodecProvider;
class SharedRpcResources;
class StorageApiRpcService;
}

struct BucketResolver;
class Visitor;
class VisitorThread;
class RPCRequestWrapper;

class StorageTransportContext : public api::TransportContext {
public:
    explicit StorageTransportContext(std::unique_ptr<documentapi::DocumentMessage> msg);
    explicit StorageTransportContext(std::unique_ptr<RPCRequestWrapper> request);
    ~StorageTransportContext() override;

    std::unique_ptr<documentapi::DocumentMessage> _docAPIMsg;
    std::unique_ptr<RPCRequestWrapper>            _request;
};

class CommunicationManager final
    : public StorageLink,
      public framework::Runnable,
      private config::IFetcherCallback<vespa::config::content::core::StorCommunicationmanagerConfig>,
      public mbus::IMessageHandler,
      public mbus::IReplyHandler,
      private framework::MetricUpdateHook,
      public MessageDispatcher
{
private:
    CommunicationManager(const CommunicationManager&);
    CommunicationManager& operator=(const CommunicationManager&);

    StorageComponent _component;
    CommunicationManagerMetrics _metrics;

    std::unique_ptr<rpc::SharedRpcResources> _shared_rpc_resources;
    std::unique_ptr<rpc::StorageApiRpcService> _storage_api_rpc_service;
    std::unique_ptr<rpc::ClusterControllerApiRpcService> _cc_rpc_service;
    std::unique_ptr<rpc::MessageCodecProvider> _message_codec_provider;
    Queue _eventQueue;
    // XXX: Should perhaps use a configsubscriber and poll from StorageComponent ?
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    using EarlierProtocol = std::pair<framework::SecondTime, mbus::IProtocol::SP>;
    using EarlierProtocols = std::vector<EarlierProtocol>;
    std::mutex       _earlierGenerationsLock;
    EarlierProtocols _earlierGenerations;

    void onOpen() override;
    void onClose() override;

    void process(const std::shared_ptr<api::StorageMessage>& msg);

    using CommunicationManagerConfig = vespa::config::content::core::StorCommunicationmanagerConfig;
    using BucketspacesConfig = vespa::config::content::core::BucketspacesConfig;

    void configureMessageBusLimits(const CommunicationManagerConfig& cfg);
    void configure(std::unique_ptr<CommunicationManagerConfig> config) override;
    void receiveStorageReply(const std::shared_ptr<api::StorageReply>&);
    void fail_with_unresolvable_bucket_space(std::unique_ptr<documentapi::DocumentMessage> msg,
                                             const vespalib::string& error_message);

    void serializeNodeState(const api::GetNodeStateReply& gns, std::ostream& os, bool includeDescription) const;

    static const uint64_t FORWARDED_MESSAGE = 0;

    std::unique_ptr<mbus::RPCMessageBus> _mbus;
    std::unique_ptr<mbus::DestinationSession> _messageBusSession;
    std::unique_ptr<mbus::SourceSession> _sourceSession;

    std::mutex _messageBusSentLock;
    std::map<api::StorageMessage::Id, std::shared_ptr<api::StorageCommand> > _messageBusSent;

    config::ConfigUri     _configUri;
    std::atomic<bool>     _closed;
    DocumentApiConverter  _docApiConverter;
    framework::Thread::UP _thread;

    void updateMetrics(const MetricLockGuard &) override;

    // Test needs access to configure() for live reconfig testing.
    friend struct CommunicationManagerTest;

public:
    CommunicationManager(StorageComponentRegister& compReg,
                         const config::ConfigUri & configUri);
    ~CommunicationManager() override;

    // MessageDispatcher overrides
    void dispatch_sync(std::shared_ptr<api::StorageMessage> msg) override;
    void dispatch_async(std::shared_ptr<api::StorageMessage> msg) override;

    mbus::RPCMessageBus& getMessageBus() { return *_mbus; }
    const PriorityConverter& getPriorityConverter() const { return _docApiConverter.getPriorityConverter(); }

    /**
     * From StorageLink. Called when messages arrive from storage
     * modules. Will convert and dispatch messages to MessageServer
     */
    bool onUp(const std::shared_ptr<api::StorageMessage>&) override;
    bool sendCommand(const std::shared_ptr<api::StorageCommand>& command);
    bool sendReply(const std::shared_ptr<api::StorageReply>& reply);
    void sendDirectRPCReply(RPCRequestWrapper& request, const std::shared_ptr<api::StorageReply>& reply);
    void sendMessageBusReply(StorageTransportContext& context, const std::shared_ptr<api::StorageReply>& reply);

    // Pump thread
    void run(framework::ThreadHandle&) override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void handleMessage(std::unique_ptr<mbus::Message> msg) override;
    void sendMessageBusMessage(const std::shared_ptr<api::StorageCommand>& msg,
                               std::unique_ptr<mbus::Message> mbusMsg, const mbus::Route& route);

    void handleReply(std::unique_ptr<mbus::Reply> msg) override;
    void updateMessagebusProtocol(const std::shared_ptr<const document::DocumentTypeRepo> &repo);
    void updateBucketSpacesConfig(const BucketspacesConfig&);

    const CommunicationManagerMetrics& metrics() const noexcept { return _metrics; }

    // Intended primarily for unit tests that fire up multiple nodes and must wait until all
    // nodes are cross-visible in Slobrok before progressing.
    [[nodiscard]] bool address_visible_in_slobrok(const api::StorageMessageAddress& addr) const noexcept;
};

} // storage
