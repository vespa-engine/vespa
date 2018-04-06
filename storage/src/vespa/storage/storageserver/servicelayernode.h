// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/storage/bucketdb/minimumusedbitstracker.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/config/config.h>

namespace storage {

class FileStorManager;

class ServiceLayerNode
        : public StorageNode,
          private VisitorMessageSessionFactory

{
    ServiceLayerNodeContext& _context;
    spi::PersistenceProvider& _persistenceProvider;
    spi::PartitionStateList _partitions;
    VisitorFactory::Map _externalVisitors;
    MinimumUsedBitsTracker _minUsedBitsTracker;

    // FIXME: Should probably use the fetcher in StorageNode
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    FileStorManager* _fileStorManager;
    bool _init_has_been_called;
    bool _noUsablePartitionMode;

public:
    typedef std::unique_ptr<ServiceLayerNode> UP;

    ServiceLayerNode(const config::ConfigUri & configUri,
                     ServiceLayerNodeContext& context,
                     ApplicationGenerationFetcher& generationFetcher,
                     spi::PersistenceProvider& persistenceProvider,
                     const VisitorFactory::Map& externalVisitors);
    ~ServiceLayerNode();
    /**
     * Init must be called exactly once after construction and before destruction.
     */
    void init();

    const lib::NodeType& getNodeType() const override { return lib::NodeType::STORAGE; }

    ResumeGuard pause() override;

private:
    void subscribeToConfigs() override;
    void initializeNodeSpecific() override;
    void handleLiveConfigUpdate(const InitialGuard & initGuard) override;
    VisitorMessageSession::UP createSession(Visitor&, VisitorThread&) override;
    documentapi::Priority::Value toDocumentPriority(uint8_t storagePriority) const override;
    std::unique_ptr<StorageLink> createChain() override;
    void removeConfigSubscriptions() override;
};

} // storage
