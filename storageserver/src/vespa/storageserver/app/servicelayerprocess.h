// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "process.h"
#include <vespa/config-persistence.h>
#include <vespa/config-stor-filestor.h>
#include <vespa/storage/common/visitorfactory.h>
#include <vespa/storage/storageserver/servicelayernodecontext.h>
#include <vespa/storage/visiting/config-stor-visitor.h>
#include <vespa/vespalib/util/hw_info.h>

namespace config { class ConfigUri; }

namespace storage {

namespace spi { struct PersistenceProvider; }

class ServiceLayerNode;
class IStorageChainBuilder;

class ServiceLayerProcess : public Process {
protected:
    VisitorFactory::Map _externalVisitors;
private:
    using PersistenceConfig  = vespa::config::content::PersistenceConfig;
    using StorVisitorConfig  = vespa::config::content::core::StorVisitorConfig;
    using StorFilestorConfig = vespa::config::content::StorFilestorConfig;

    std::unique_ptr<config::ConfigHandle<PersistenceConfig>>  _persistence_cfg_handle;
    std::unique_ptr<config::ConfigHandle<StorVisitorConfig>>  _visitor_cfg_handle;
    std::unique_ptr<config::ConfigHandle<StorFilestorConfig>> _filestor_cfg_handle;

    std::unique_ptr<ServiceLayerNode>     _node;
    std::unique_ptr<IStorageChainBuilder> _storage_chain_builder;

protected:
    vespalib::HwInfo        _hw_info;
    ServiceLayerNodeContext _context;

public:
    ServiceLayerProcess(const config::ConfigUri & configUri, const vespalib::HwInfo& hw_info);
    ~ServiceLayerProcess() override;

    void shutdown() override;

    void setupConfig(vespalib::duration subscribe_timeout) override;
    bool configUpdated() override;
    void updateConfig() override;

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

