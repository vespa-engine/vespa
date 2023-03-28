// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ServiceLayerNode
 * \ingroup storageserver
 *
 * \brief Class for setting up a service layer node.
 */

#pragma once

#include "applicationgenerationfetcher.h"
#include "servicelayernodecontext.h"
#include "storagenode.h"
#include <vespa/storage/visiting/visitormessagesessionfactory.h>
#include <vespa/storage/common/visitorfactory.h>

namespace storage {

namespace spi { struct PersistenceProvider; }

class BucketManager;
class FileStorManager;

class ServiceLayerNode
        : public StorageNode,
          private VisitorMessageSessionFactory

{
    ServiceLayerNodeContext& _context;
    spi::PersistenceProvider& _persistenceProvider;
    VisitorFactory::Map _externalVisitors;

    // FIXME: Should probably use the fetcher in StorageNode
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    BucketManager* _bucket_manager;
    FileStorManager* _fileStorManager;
    bool _init_has_been_called;

public:
    using UP = std::unique_ptr<ServiceLayerNode>;

    ServiceLayerNode(const config::ConfigUri & configUri,
                     ServiceLayerNodeContext& context,
                     ApplicationGenerationFetcher& generationFetcher,
                     spi::PersistenceProvider& persistenceProvider,
                     const VisitorFactory::Map& externalVisitors);
    ~ServiceLayerNode() override;
    /**
     * Init must be called exactly once after construction and before destruction.
     */
    void init();

    const lib::NodeType& getNodeType() const override { return lib::NodeType::STORAGE; }

    ResumeGuard pause() override;

private:
    void subscribeToConfigs() override;
    void initializeNodeSpecific() override;
    void perform_post_chain_creation_init_steps() override;
    void handleLiveConfigUpdate(const InitialGuard & initGuard) override;
    VisitorMessageSession::UP createSession(Visitor&, VisitorThread&) override;
    documentapi::Priority::Value toDocumentPriority(uint8_t storagePriority) const override;
    void createChain(IStorageChainBuilder &builder) override;
    void removeConfigSubscriptions() override;
};

} // storage
