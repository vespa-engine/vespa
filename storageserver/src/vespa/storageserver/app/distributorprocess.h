// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DistributorProcess
 *
 * \brief A process running a distributor.
 */

#pragma once

#include "process.h"
#include <vespa/storage/storageserver/distributornode.h>

namespace storage {

class IStorageChainBuilder;

class DistributorProcess final : public Process {
    DistributorNodeContext _context;
    uint32_t _num_distributor_stripes;
    DistributorNode::UP _node;
    config::ConfigHandle<vespa::config::content::core::StorDistributormanagerConfig>::UP
            _distributorConfigHandler;
    config::ConfigHandle<vespa::config::content::core::StorVisitordispatcherConfig>::UP
            _visitDispatcherConfigHandler;
    std::unique_ptr<IStorageChainBuilder> _storage_chain_builder;

public:
    explicit DistributorProcess(const config::ConfigUri & configUri);
    ~DistributorProcess() override;

    void shutdown() override;
    void setupConfig(vespalib::duration subscribeTimeout) override;
    void createNode() override;
    bool configUpdated() override;
    void updateConfig() override;
    StorageNode& getNode() override { return *_node; }
    StorageNodeContext& getContext() override { return _context; }
    std::string getComponentName() const override { return "distributor"; }

    virtual DistributorNodeContext& getDistributorContext() { return _context; }
    void set_storage_chain_builder(std::unique_ptr<IStorageChainBuilder> builder);
};

} // storage
