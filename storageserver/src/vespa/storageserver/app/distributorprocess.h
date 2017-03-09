// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DistributorProcess
 *
 * \brief A process running a distributor.
 */

#pragma once

#include <vespa/storageserver/app/process.h>

namespace storage {

class DistributorProcess : public Process {
    DistributorNodeContext _context;
    DistributorNode::NeedActiveState _activeFlag;
    DistributorNode::UP _node;
    config::ConfigHandle<vespa::config::content::core::StorDistributormanagerConfig>::UP
            _distributorConfigHandler;
    config::ConfigHandle<vespa::config::content::core::StorVisitordispatcherConfig>::UP
            _visitDispatcherConfigHandler;

public:
    DistributorProcess(const config::ConfigUri & configUri);
    ~DistributorProcess();

    virtual void shutdown();

    virtual void setupConfig(uint64_t subscribeTimeout);
    virtual void createNode();
    virtual bool configUpdated();
    virtual void updateConfig();

    virtual StorageNode& getNode() { return *_node; }
    virtual StorageNodeContext& getContext() { return _context; }
    virtual DistributorNodeContext& getDistributorContext() { return _context; }

    virtual std::string getComponentName() const { return "distributor"; }
};

} // storage

