// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "applicationgenerationfetcher.h"
#include "servicelayernodecontext.h"
#include "storagenode.h"
#include "vespa/vespalib/util/jsonstream.h"
#include <vespa/storage/visiting/visitormessagesessionfactory.h>
#include <vespa/storage/common/visitorfactory.h>
#include <vespa/storage/common/nodestateupdater.h>

namespace storage {

namespace spi { struct PersistenceProvider; }

class Bouncer;
class BucketManager;
class FileStorManager;
class MergeThrottler;

class ServiceLayerNode
        : public StorageNode,
          private VisitorMessageSessionFactory,
          private NodeStateReporter

{
    ServiceLayerNodeContext&  _context;
    spi::PersistenceProvider& _persistenceProvider;
    VisitorFactory::Map       _externalVisitors;

    Bouncer*                  _bouncer;
    BucketManager*            _bucket_manager;
    FileStorManager*          _fileStorManager;
    MergeThrottler*           _merge_throttler;
    bool                      _init_has_been_called;

public:
    using UP = std::unique_ptr<ServiceLayerNode>;

    ServiceLayerNode(const config::ConfigUri & configUri,
                     ServiceLayerNodeContext& context,
                     BootstrapConfigs bootstrap_configs,
                     ApplicationGenerationFetcher& generationFetcher,
                     spi::PersistenceProvider& persistenceProvider,
                     const VisitorFactory::Map& externalVisitors);
    ~ServiceLayerNode() override;
    /**
     * Init must be called exactly once after construction and before destruction.
     */
    void init();

    void on_configure(const StorServerConfig& config);

    const lib::NodeType& getNodeType() const override { return lib::NodeType::STORAGE; }

    ResumeGuard pause() override;

private:
    void report(vespalib::JsonStream &writer) const override;
    void initializeNodeSpecific() override;
    void perform_post_chain_creation_init_steps() override;
    void handleLiveConfigUpdate(const InitialGuard & initGuard) override;
    VisitorMessageSession::UP createSession(Visitor&, VisitorThread&) override;
    documentapi::Priority::Value toDocumentPriority(uint8_t storagePriority) const override;
    void createChain(IStorageChainBuilder &builder) override;
    void on_bouncer_config_changed() override;
};

} // storage
