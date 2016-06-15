// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vespa/memfilepersistence/spi/memfilepersistenceprovider.h>
#include <vespa/storageserver/app/servicelayerprocess.h>
#include <vespa/vespalib/util/sync.h>

namespace storage {

class MemFileServiceLayerProcess
        : public ServiceLayerProcess,
          public config::IFetcherCallback<vespa::config::storage::StorMemfilepersistenceConfig>,
          public config::IFetcherCallback<vespa::config::storage::StorDevicesConfig>,
          public config::IFetcherCallback<vespa::config::content::PersistenceConfig>
{
    bool _changed;
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    std::unique_ptr<vespa::config::storage::StorMemfilepersistenceConfig> _nextMemfilepersistence;
    std::unique_ptr<vespa::config::storage::StorDevicesConfig> _nextDevices;
    std::unique_ptr<vespa::config::content::PersistenceConfig> _nextPersistence;
    memfile::MemFilePersistenceProvider::UP _provider;
    vespalib::Lock _lock;

public:
    MemFileServiceLayerProcess(const config::ConfigUri & configUri);
    ~MemFileServiceLayerProcess() { shutdown(); }

    virtual void shutdown();

    void setupConfig(uint64_t subscribeTimeout);
    virtual void removeConfigSubscriptions();
    virtual void setupProvider();
    virtual bool configUpdated();
    virtual void updateConfig();

    virtual spi::PersistenceProvider& getProvider() { return *_provider; }

    void configure(std::unique_ptr<vespa::config::storage::StorMemfilepersistenceConfig> config);
    void configure(std::unique_ptr<vespa::config::storage::StorDevicesConfig> config);
    void configure(std::unique_ptr<vespa::config::content::PersistenceConfig> config);
};

} // storage

