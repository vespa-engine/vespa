// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ServiceLayerProcess
 *
 * \brief A process running a service layer.
 */
/**
 * \class storage::MemFileServiceLayerProcess
 *
 * \brief A process running a service layer with memfile persistence provider.
 */
/**
 * \class storage::RpcServiceLayerProcess
 *
 * \brief A process running a service layer with RPC persistence provider.
 */
#pragma once

#include "process.h"
#include <vespa/storage/storageserver/servicelayernodecontext.h>
#include <vespa/storage/common/visitorfactory.h>

namespace config { class ConfigUri; }

namespace storage {

namespace spi { struct PersistenceProvider; }

class ServiceLayerNode;
class IStorageChainBuilder;

class ServiceLayerProcess : public Process {
protected:
    VisitorFactory::Map _externalVisitors;
private:
    std::unique_ptr<ServiceLayerNode> _node;
    std::unique_ptr<IStorageChainBuilder> _storage_chain_builder;

protected:
    ServiceLayerNodeContext _context;

public:
    explicit ServiceLayerProcess(const config::ConfigUri & configUri);
    ~ServiceLayerProcess() override;

    void shutdown() override;

    virtual void setupProvider() = 0;
    virtual spi::PersistenceProvider& getProvider() = 0;

    void createNode() override;
    StorageNode& getNode() override;
    StorageNodeContext& getContext() override;
    std::string getComponentName() const override;
    void set_storage_chain_builder(std::unique_ptr<IStorageChainBuilder> builder);
    virtual void add_external_visitors();
};

} // storage

