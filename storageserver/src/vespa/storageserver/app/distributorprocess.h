// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DistributorProcess
 *
 * \brief A process running a distributor.
 */

#pragma once

#include "process.h"
#include <vespa/storage/storageserver/distributornode.h>

namespace storage {

class DistributorProcess : public Process {
    DistributorNodeContext _context;
    DistributorNode::NeedActiveState _activeFlag;
    bool _use_btree_database;
    DistributorNode::UP _node;
    config::ConfigHandle<vespa::config::content::core::StorDistributormanagerConfig>::UP
            _distributorConfigHandler;
    config::ConfigHandle<vespa::config::content::core::StorVisitordispatcherConfig>::UP
            _visitDispatcherConfigHandler;

public:
    DistributorProcess(const config::ConfigUri & configUri);
    ~DistributorProcess() override;

    void shutdown() override;
    void setupConfig(milliseconds subscribeTimeout) override;
    void createNode() override;
    bool configUpdated() override;
    void updateConfig() override;
    StorageNode& getNode() override { return *_node; }
    StorageNodeContext& getContext() override { return _context; }
    std::string getComponentName() const override { return "distributor"; }

    virtual DistributorNodeContext& getDistributorContext() { return _context; }
};

} // storage
