// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "applicationgenerationfetcher.h"
#include "servicelayernodecontext.h"
#include "storagenode.h"
#include "vespa/vespalib/util/jsonstream.h"
#include <vespa/config-persistence.h>
#include <vespa/config-stor-filestor.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/visitorfactory.h>
#include <vespa/storage/visiting/config-stor-visitor.h>
#include <vespa/storage/visiting/visitormessagesessionfactory.h>
#include <vespa/vespalib/util/hw_info.h>

namespace storage {

namespace spi { struct PersistenceProvider; }

class Bouncer;
class BucketManager;
class ChangedBucketOwnershipHandler;
class FileStorManager;
class MergeThrottler;
class ModifiedBucketChecker;
class VisitorManager;

class ServiceLayerNode
        : public StorageNode,
          private VisitorMessageSessionFactory,
          private NodeStateReporter

{
public:
    using PersistenceConfig  = vespa::config::content::PersistenceConfig;
    using StorVisitorConfig  = vespa::config::content::core::StorVisitorConfig;
    using StorFilestorConfig = vespa::config::content::StorFilestorConfig;
private:
    ServiceLayerNodeContext&            _context;
    spi::PersistenceProvider&           _persistenceProvider;
    VisitorFactory::Map                 _externalVisitors;
    vespalib::HwInfo                    _hw_info;
    std::unique_ptr<PersistenceConfig>  _persistence_bootstrap_config;
    std::unique_ptr<StorVisitorConfig>  _visitor_bootstrap_config;
    std::unique_ptr<StorFilestorConfig> _filestor_bootstrap_config;
    Bouncer*                            _bouncer;
    BucketManager*                      _bucket_manager;
    ChangedBucketOwnershipHandler*      _changed_bucket_ownership_handler;
    FileStorManager*                    _fileStorManager;
    MergeThrottler*                     _merge_throttler;
    VisitorManager*                     _visitor_manager;
    ModifiedBucketChecker*              _modified_bucket_checker;
    bool                                _init_has_been_called;

public:
    using UP = std::unique_ptr<ServiceLayerNode>;

    struct ServiceLayerBootstrapConfigs {
        BootstrapConfigs storage_bootstrap_configs;
        std::unique_ptr<PersistenceConfig>  persistence_cfg;
        std::unique_ptr<StorVisitorConfig>  visitor_cfg;
        std::unique_ptr<StorFilestorConfig> filestor_cfg;

        ServiceLayerBootstrapConfigs();
        ~ServiceLayerBootstrapConfigs();
        ServiceLayerBootstrapConfigs(ServiceLayerBootstrapConfigs&&) noexcept;
        ServiceLayerBootstrapConfigs& operator=(ServiceLayerBootstrapConfigs&&) noexcept;
    };

    ServiceLayerNode(const config::ConfigUri& configUri,
                     ServiceLayerNodeContext& context,
                     const vespalib::HwInfo& hw_info,
                     ServiceLayerBootstrapConfigs bootstrap_configs,
                     ApplicationGenerationFetcher& generationFetcher,
                     spi::PersistenceProvider& persistenceProvider,
                     const VisitorFactory::Map& externalVisitors);
    ~ServiceLayerNode() override;
    /**
     * Init must be called exactly once after construction and before destruction.
     */
    void init();

    void on_configure(const StorServerConfig& config);
    void on_configure(const PersistenceConfig& config);
    void on_configure(const StorVisitorConfig& config);
    void on_configure(const StorFilestorConfig& config);

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
