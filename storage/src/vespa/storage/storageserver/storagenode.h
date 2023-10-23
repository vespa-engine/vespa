// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Main storage server class.
 *
 * This class sets up the entire storage server.
 *
 * @author Håkon Humberset
 */

#pragma once

#include <vespa/config-bucketspaces.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/config/config-stor-bouncer.h>
#include <vespa/storage/config/config-stor-communicationmanager.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/storageutil/resumeguard.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <atomic>
#include <mutex>

namespace document { class DocumentTypeRepo; }
namespace config { class ConfigFetcher; }

namespace storage {

class ApplicationGenerationFetcher;
class CommunicationManager;
class FileStorManager;
class HostInfo;
class IStorageChainBuilder;
class NodeIdentity;
class StateManager;
class StateReporter;
class StatusMetricConsumer;
class StatusWebServer;
class StorageComponent;
class StorageLink;
class NodeStateReporter;
struct DeadLockDetector;
struct StorageMetricSet;
struct StorageNodeContext;

namespace lib { class NodeType; }


class StorageNode : private framework::MetricUpdateHook,
                    private DoneInitializeHandler,
                    private framework::defaultimplementation::ShutdownListener
{
public:
    using BucketspacesConfig         = vespa::config::content::core::BucketspacesConfig;
    using CommunicationManagerConfig = vespa::config::content::core::StorCommunicationmanagerConfig;
    using StorBouncerConfig          = vespa::config::content::core::StorBouncerConfig;
    using StorDistributionConfig     = vespa::config::content::StorDistributionConfig;
    using StorServerConfig           = vespa::config::content::core::StorServerConfig;

    enum RunMode { NORMAL, SINGLE_THREADED_TEST_MODE };

    StorageNode(const StorageNode &) = delete;
    StorageNode & operator = (const StorageNode &) = delete;

    struct BootstrapConfigs {
        std::unique_ptr<StorBouncerConfig>          bouncer_cfg;
        std::unique_ptr<BucketspacesConfig>         bucket_spaces_cfg;
        std::unique_ptr<CommunicationManagerConfig> comm_mgr_cfg;
        std::unique_ptr<StorDistributionConfig>     distribution_cfg;
        std::unique_ptr<StorServerConfig>           server_cfg;

        BootstrapConfigs();
        ~BootstrapConfigs();
        BootstrapConfigs(BootstrapConfigs&&) noexcept;
        BootstrapConfigs& operator=(BootstrapConfigs&&) noexcept;
    };

    StorageNode(const config::ConfigUri& configUri,
                StorageNodeContext& context,
                BootstrapConfigs bootstrap_configs,
                ApplicationGenerationFetcher& generationFetcher,
                std::unique_ptr<HostInfo> hostInfo,
                RunMode = NORMAL);
    ~StorageNode() override;

    virtual const lib::NodeType& getNodeType() const = 0;
    [[nodiscard]] bool attemptedStopped() const;
    void notifyDoneInitializing() override;
    void waitUntilInitialized(vespalib::duration timeout = 15s);
    void updateMetrics(const MetricLockGuard & guard) override;

    /** Updates the document type repo. */
    void setNewDocumentRepo(const std::shared_ptr<const document::DocumentTypeRepo>& repo);

    /**
     * Pauses the persistence processing. While the returned ResumeGuard
     * is alive, no calls will be made towards the persistence provider.
     */
    virtual ResumeGuard pause() = 0;
    void requestShutdown(vespalib::stringref reason) override;
    DoneInitializeHandler& getDoneInitializeHandler() { return *this; }

    void configure(std::unique_ptr<StorServerConfig> config);
    void configure(std::unique_ptr<StorDistributionConfig> config);
    void configure(std::unique_ptr<BucketspacesConfig>);
    void configure(std::unique_ptr<CommunicationManagerConfig> config);
    void configure(std::unique_ptr<StorBouncerConfig> config);

    // For testing
    StorageLink* getChain() { return _chain.get(); }
    virtual void initializeStatusWebServer();
private:
    bool _singleThreadedDebugMode;

    std::unique_ptr<HostInfo> _hostInfo;

    StorageNodeContext& _context;
    ApplicationGenerationFetcher& _generationFetcher;
    vespalib::string _rootFolder;
    std::atomic<bool> _attemptedStopped;
    vespalib::string _pidFile;

    // First components that doesn't depend on others
    std::unique_ptr<StatusWebServer>           _statusWebServer;
    std::shared_ptr<StorageMetricSet>          _metrics;
    std::unique_ptr<metrics::MetricManager>    _metricManager;

    // Depends on bucket databases and stop() functionality
    std::unique_ptr<DeadLockDetector>          _deadLockDetector;
    // Depends on metric manager
    std::unique_ptr<StatusMetricConsumer>      _statusMetrics;
    // Depends on metric manager
    std::unique_ptr<StateReporter>             _stateReporter;
    std::unique_ptr<StateManager>              _stateManager;

    // The storage chain can depend on anything.
    std::unique_ptr<StorageLink>               _chain;

    template <typename ConfigT>
    struct ConfigWrapper {
        std::unique_ptr<ConfigT> staging;
        std::unique_ptr<ConfigT> active;

        ConfigWrapper() noexcept;
        explicit ConfigWrapper(std::unique_ptr<ConfigT> initial_active) noexcept;
        ~ConfigWrapper();

        void promote_staging_to_active() noexcept;
    };

    template <typename ConfigT>
    void stage_config_change(ConfigWrapper<ConfigT>& my_cfg, std::unique_ptr<ConfigT> new_cfg);

protected:
    // Lock taken while doing configuration of the server.
    std::mutex _configLock;
    std::mutex _initial_config_mutex; // TODO can probably be removed
    using InitialGuard = std::lock_guard<std::mutex>;

    ConfigWrapper<StorBouncerConfig>          _bouncer_config;
    ConfigWrapper<BucketspacesConfig>         _bucket_spaces_config;
    ConfigWrapper<CommunicationManagerConfig> _comm_mgr_config;
    ConfigWrapper<StorDistributionConfig>     _distribution_config;
    ConfigWrapper<StorServerConfig>           _server_config;

    [[nodiscard]] const StorBouncerConfig& bouncer_config() const noexcept {
        return *_bouncer_config.active;
    }
    [[nodiscard]] const BucketspacesConfig& bucket_spaces_config() const noexcept {
        return *_bucket_spaces_config.active;
    }
    [[nodiscard]] const CommunicationManagerConfig& communication_manager_config() const noexcept {
        return *_comm_mgr_config.active;
    }
    [[nodiscard]] const StorDistributionConfig& distribution_config() const noexcept {
        return *_distribution_config.active;
    }
    [[nodiscard]] const StorServerConfig& server_config() const noexcept {
        return *_server_config.active;
    }

    std::unique_ptr<StorageComponent> _component;
    std::unique_ptr<NodeIdentity> _node_identity;
    config::ConfigUri _configUri;
    CommunicationManager* _communicationManager;
private:
    std::unique_ptr<IStorageChainBuilder>      _chain_builder;
protected:

    /**
     * Node subclasses currently need to explicitly acquire ownership of state
     * manager so that they can add it to the end of their processing chains,
     * which this method allows for.
     * Any component releasing the state manager must ensure it lives for as
     * long as the node instance itself lives.
     */
    std::unique_ptr<StateManager> releaseStateManager();

    void initialize(const NodeStateReporter & reporter);
    virtual void initializeNodeSpecific() = 0;
    virtual void perform_post_chain_creation_init_steps() = 0;
    virtual void createChain(IStorageChainBuilder &builder) = 0;
    virtual void handleLiveConfigUpdate(const InitialGuard & initGuard);
    void shutdown();

    virtual void on_bouncer_config_changed() { /* no-op by default */ }
public:
    void set_storage_chain_builder(std::unique_ptr<IStorageChainBuilder> builder);
};

} // storage
