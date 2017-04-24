// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StorageNode
 * @ingroup storageserver
 *
 * @brief Main storage server class.
 *
 * This class sets up the entire storage server.
 *
 * @author Håkon Humberset
 */

#pragma once

#include "storagemetricsset.h"
#include "storagenodecontext.h"
#include "applicationgenerationfetcher.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/storage/config/config-stor-server.h>

#include <vespa/config/helper/legacysubscriber.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/metrics/metrics.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/visitorfactory.h>
#include <vespa/storage/config/config-stor-prioritymapping.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/storage/frameworkimpl/status/statuswebserver.h>
#include <vespa/storage/frameworkimpl/thread/deadlockdetector.h>
#include <vespa/storageframework/defaultimplementation/memory/memorymanager.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <vespa/storage/frameworkimpl/memory/memorystatusviewer.h>
#include <vespa/storage/visiting/visitormessagesessionfactory.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storage/storageutil/resumeguard.h>
#include <vespa/config-upgrading.h>
#include <vespa/config-stor-distribution.h>

namespace storage {

class StatusMetricConsumer;
class StateReporter;
class CommunicationManager;
class FileStorManager;
class HostInfo;
class StateManager;

class StorageNode : private config::IFetcherCallback<vespa::config::content::core::StorServerConfig>,
                    private config::IFetcherCallback<vespa::config::content::StorDistributionConfig>,
                    private config::IFetcherCallback<vespa::config::content::UpgradingConfig>,
                    private config::IFetcherCallback<vespa::config::content::core::StorPrioritymappingConfig>,
                    private framework::MetricUpdateHook,
                    private DoneInitializeHandler,
                    private framework::defaultimplementation::ShutdownListener
{
public:
    enum RunMode { NORMAL, SINGLE_THREADED_TEST_MODE };

    StorageNode(const StorageNode &) = delete;
    StorageNode & operator = (const StorageNode &) = delete;
    /**
     * @param excludeStorageChain With this option set, no chain will be set
     * up. This can be useful in unit testing if you need a storage server
     * instance, but you want to have full control over the components yourself.
     */
    StorageNode(const config::ConfigUri & configUri,
                StorageNodeContext& context,
                ApplicationGenerationFetcher& generationFetcher,
                std::unique_ptr<HostInfo> hostInfo,
                RunMode = NORMAL);
    virtual ~StorageNode();

    virtual const lib::NodeType& getNodeType() const = 0;
    bool attemptedStopped() const;
    void notifyDoneInitializing() override;
    void waitUntilInitialized(uint32_t timeoutSeconds = 15);
    void updateMetrics(const MetricLockGuard & guard) override;

    /** Updates the document type repo. */
    void setNewDocumentRepo(const document::DocumentTypeRepo::SP& repo);

    /**
     * Pauses the persistence processing. While the returned ResumeGuard
     * is alive, no calls will be made towards the persistence provider.
     */
    virtual ResumeGuard pause() = 0;
    void requestShutdown(vespalib::stringref reason) override;
    void notifyPartitionDown(int partId, vespalib::stringref reason);
    DoneInitializeHandler& getDoneInitializeHandler() { return *this; }

    // For testing
    StorageLink* getChain() { return _chain.get(); }
    virtual void initializeStatusWebServer();

private:
    bool _singleThreadedDebugMode;
        // Subscriptions to config
    std::unique_ptr<config::ConfigFetcher> _configFetcher;

    std::unique_ptr<HostInfo> _hostInfo;

    StorageNodeContext& _context;
    ApplicationGenerationFetcher& _generationFetcher;
    vespalib::string _rootFolder;
    bool _attemptedStopped;
    vespalib::string _pidFile;

        // First components that doesn't depend on others
    std::unique_ptr<StatusWebServer>           _statusWebServer;
    std::shared_ptr<StorageMetricSet>        _metrics;
    std::unique_ptr<metrics::MetricManager>    _metricManager;

        // Depends on bucket databases and stop() functionality
    std::unique_ptr<DeadLockDetector>          _deadLockDetector;
        // Depends on dead lock detector and threadpool
    std::unique_ptr<MemoryStatusViewer>        _memoryStatusViewer;
        // Depends on metric manager
    std::unique_ptr<StatusMetricConsumer>      _statusMetrics;
        // Depends on metric manager
    std::unique_ptr<StateReporter>             _stateReporter;

    std::unique_ptr<StateManager> _stateManager;

        // The storage chain can depend on anything.
    std::unique_ptr<StorageLink>               _chain;

    /** Implementation of config callbacks. */
    void configure(std::unique_ptr<vespa::config::content::core::StorServerConfig> config) override;
    void configure(std::unique_ptr<vespa::config::content::UpgradingConfig> config) override;
    void configure(std::unique_ptr<vespa::config::content::StorDistributionConfig> config) override;
    void configure(std::unique_ptr<vespa::config::content::core::StorPrioritymappingConfig>) override;
    virtual void configure(std::unique_ptr<document::DocumenttypesConfig> config,
                           bool hasChanged, int64_t generation);
    void updateUpgradeFlag(const vespa::config::content::UpgradingConfig&);

protected:
        // Lock taken while doing configuration of the server.
    vespalib::Lock _configLock;
        // Current running config. Kept, such that we can see what has been
        // changed in live config updates.
    std::unique_ptr<vespa::config::content::core::StorServerConfig> _serverConfig;
    std::unique_ptr<vespa::config::content::UpgradingConfig> _clusterConfig;
    std::unique_ptr<vespa::config::content::StorDistributionConfig> _distributionConfig;
    std::unique_ptr<vespa::config::content::core::StorPrioritymappingConfig> _priorityConfig;
    std::unique_ptr<document::DocumenttypesConfig> _doctypesConfig;
        // New configs gotten that has yet to have been handled
    std::unique_ptr<vespa::config::content::core::StorServerConfig> _newServerConfig;
    std::unique_ptr<vespa::config::content::UpgradingConfig> _newClusterConfig;
    std::unique_ptr<vespa::config::content::StorDistributionConfig> _newDistributionConfig;
    std::unique_ptr<vespa::config::content::core::StorPrioritymappingConfig> _newPriorityConfig;
    std::unique_ptr<document::DocumenttypesConfig> _newDoctypesConfig;
    StorageComponent::UP _component;
    config::ConfigUri _configUri;
    CommunicationManager* _communicationManager;

    /**
     * Node subclasses currently need to explicitly acquire ownership of state
     * manager so that they can add it to the end of their processing chains,
     * which this method allows for.
     * Any component releasing the state manager must ensure it lives for as
     * long as the node instance itself lives.
     */
    std::unique_ptr<StateManager> releaseStateManager();

    void initialize();
    virtual void subscribeToConfigs();
    virtual void initializeNodeSpecific() = 0;
    virtual StorageLink::UP createChain() = 0;
    virtual void handleLiveConfigUpdate();
    void shutdown();
    virtual void removeConfigSubscriptions();
};

} // storage
