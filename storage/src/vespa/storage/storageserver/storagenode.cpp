// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "communicationmanager.h"
#include "config_logging.h"
#include "statemanager.h"
#include "statereporter.h"
#include "storagemetricsset.h"
#include "storagenode.h"
#include "storagenodecontext.h"

#include <vespa/metrics/metricmanager.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/statusmetricconsumer.h>
#include <vespa/storage/common/storage_chain_builder.h>
#include <vespa/storage/frameworkimpl/status/statuswebserver.h>
#include <vespa/storage/frameworkimpl/thread/deadlockdetector.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/time.h>
#include <fcntl.h>
#include <filesystem>

#include <vespa/log/log.h>

LOG_SETUP(".node.server");

using vespa::config::content::StorDistributionConfigBuilder;
using vespa::config::content::core::StorServerConfigBuilder;
using std::make_shared;

namespace storage {

namespace {

using vespalib::getLastErrorString;

void
writePidFile(const vespalib::string& pidfile)
{
    ssize_t rv = -1;
    vespalib::string mypid = vespalib::make_string("%d\n", getpid());
    size_t lastSlash = pidfile.rfind('/');
    if (lastSlash != vespalib::string::npos) {
        std::filesystem::create_directories(std::filesystem::path(pidfile.substr(0, lastSlash)));
    }
    int fd = open(pidfile.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd != -1) {
        rv = write(fd, mypid.c_str(), mypid.size());
        close(fd);
    }
    if (rv < 1) {
        LOG(warning, "Failed to write pidfile '%s': %s",
            pidfile.c_str(), getLastErrorString().c_str());
    }
}

void
removePidFile(const vespalib::string& pidfile)
{
    if (unlink(pidfile.c_str()) != 0) {
        LOG(warning, "Failed to delete pidfile '%s': %s",
            pidfile.c_str(), getLastErrorString().c_str());
    }
}

} // End of anonymous namespace

StorageNode::BootstrapConfigs::BootstrapConfigs() = default;
StorageNode::BootstrapConfigs::~BootstrapConfigs() = default;
StorageNode::BootstrapConfigs::BootstrapConfigs(BootstrapConfigs&&) noexcept = default;
StorageNode::BootstrapConfigs& StorageNode::BootstrapConfigs::operator=(BootstrapConfigs&&) noexcept = default;

StorageNode::StorageNode(
        const config::ConfigUri & configUri,
        StorageNodeContext& context,
        BootstrapConfigs bootstrap_configs,
        ApplicationGenerationFetcher& generationFetcher,
        std::unique_ptr<HostInfo> hostInfo,
        RunMode mode)
    : _singleThreadedDebugMode(mode == SINGLE_THREADED_TEST_MODE),
      _hostInfo(std::move(hostInfo)),
      _context(context),
      _generationFetcher(generationFetcher),
      _rootFolder(),
      _attemptedStopped(false),
      _pidFile(),
      _statusWebServer(),
      _metrics(),
      _metricManager(),
      _deadLockDetector(),
      _statusMetrics(),
      _stateReporter(),
      _stateManager(),
      _chain(),
      _configLock(),
      _initial_config_mutex(),
      _bouncer_config(std::move(bootstrap_configs.bouncer_cfg)),
      _bucket_spaces_config(std::move(bootstrap_configs.bucket_spaces_cfg)),
      _comm_mgr_config(std::move(bootstrap_configs.comm_mgr_cfg)),
      _distribution_config(std::move(bootstrap_configs.distribution_cfg)),
      _server_config(std::move(bootstrap_configs.server_cfg)),
      _component(),
      _node_identity(),
      _configUri(configUri),
      _communicationManager(nullptr),
      _chain_builder(std::make_unique<StorageChainBuilder>())
{
}

void
StorageNode::initialize(const NodeStateReporter & nodeStateReporter)
{
    // Avoid racing with concurrent reconfigurations before we've set up the entire
    // node component stack.
    // TODO no longer needed... probably
    std::lock_guard<std::mutex> concurrent_config_guard(_initial_config_mutex);

    _context.getComponentRegister().registerShutdownListener(*this);

    // First update some basics that doesn't depend on anything else to be
    // available
    _rootFolder = server_config().rootFolder;

    _context.getComponentRegister().setNodeInfo(server_config().clusterName, getNodeType(), server_config().nodeIndex);
    _context.getComponentRegister().setBucketIdFactory(document::BucketIdFactory());
    _context.getComponentRegister().setDistribution(make_shared<lib::Distribution>(distribution_config()));
    _context.getComponentRegister().setBucketSpacesConfig(bucket_spaces_config());
    _node_identity = std::make_unique<NodeIdentity>(server_config().clusterName, getNodeType(), server_config().nodeIndex);

    _metrics = std::make_shared<StorageMetricSet>();
    _component = std::make_unique<StorageComponent>(_context.getComponentRegister(), "storagenode");
    _component->registerMetric(*_metrics);
    if (!_context.getComponentRegister().hasMetricManager()) {
        _metricManager = std::make_unique<metrics::MetricManager>();
        _context.getComponentRegister().setMetricManager(*_metricManager);
    }
    _component->registerMetricUpdateHook(*this, 300s);

    // Initializing state manager early, as others use it init time to
    // update node state according min used bits etc.
    // Needs node type to be set right away. Needs thread pool, index and
    // dead lock detector too, but not before open()
    _stateManager = std::make_unique<StateManager>(
            _context.getComponentRegister(),
            std::move(_hostInfo),
            nodeStateReporter,
            _singleThreadedDebugMode);
    _context.getComponentRegister().setNodeStateUpdater(*_stateManager);

    // Create VDS root folder, in case it doesn't already exist.
    // Maybe better to rather fail if it doesn't exist, but tests
    // might break if we do that. Might alter later.
    std::filesystem::create_directories(std::filesystem::path(_rootFolder));

    initializeNodeSpecific();

    _statusMetrics = std::make_unique<StatusMetricConsumer>(_context.getComponentRegister(),
                                                            _context.getComponentRegister().getMetricManager());
    _stateReporter = std::make_unique<StateReporter>(_context.getComponentRegister(),
                                                     _context.getComponentRegister().getMetricManager(),
                                                     _generationFetcher);

    // Start deadlock detector
    _deadLockDetector = std::make_unique<DeadLockDetector>(_context.getComponentRegister());
    _deadLockDetector->enableWarning(server_config().enableDeadLockDetectorWarnings);
    _deadLockDetector->enableShutdown(server_config().enableDeadLockDetector);
    _deadLockDetector->setProcessSlack(vespalib::from_s(server_config().deadLockDetectorTimeoutSlack));
    _deadLockDetector->setWaitSlack(vespalib::from_s(server_config().deadLockDetectorTimeoutSlack));

    createChain(*_chain_builder);
    _chain = std::move(*_chain_builder).build();
    _chain_builder.reset();

    assert(_communicationManager != nullptr);
    _communicationManager->updateBucketSpacesConfig(bucket_spaces_config());

    perform_post_chain_creation_init_steps();

    // Start the metric manager, such that it starts generating snapshots
    // and the like. Note that at this time, all metrics should hopefully
    // have been created, such that we don't need to pay the extra cost of
    // reinitializing metric manager often.
    if ( ! _context.getComponentRegister().getMetricManager().isInitialized() ) {
        _context.getComponentRegister().getMetricManager().init(_configUri);
    }

    if (_chain) {
        LOG(debug, "Storage chain configured. Calling open()");
        _chain->open();
    }

    initializeStatusWebServer();

        // Write pid file as the last thing we do. If we fail initialization
        // due to an exception we won't run shutdown. Thus we won't remove the
        // pid file if something throws after writing it in initialization.
        // Initialize _pidfile here, such that we can know that we didn't create
        // it in shutdown code for shutdown during init.
    _pidFile = _rootFolder + "/pidfile";
    writePidFile(_pidFile);
}

void
StorageNode::initializeStatusWebServer()
{
    if (_singleThreadedDebugMode) return;
    _statusWebServer = std::make_unique<StatusWebServer>(_context.getComponentRegister(),
                                                         _context.getComponentRegister(), _configUri);
}

#define DIFFER(a) (!(oldC.a == newC.a))
#define ASSIGN(a) { oldC.a = newC.a; updated = true; }
#define DIFFERWARN(a, b) \
    if (DIFFER(a)) { LOG(warning, "Live config failure: %s.", b); }

void
StorageNode::setNewDocumentRepo(const std::shared_ptr<const document::DocumentTypeRepo>& repo)
{
    std::lock_guard configLockGuard(_configLock);
    _context.getComponentRegister().setDocumentTypeRepo(repo);
    if (_communicationManager != nullptr) {
        _communicationManager->updateMessagebusProtocol(repo);
    }
}

void
StorageNode::handleLiveConfigUpdate(const InitialGuard & initGuard)
{
    // Make sure we don't conflict with initialize or shutdown threads.
    (void) initGuard;
    std::lock_guard configLockGuard(_configLock);

    assert(_chain);
    // If we get here, initialize is done running. We have to handle changes
    // we want to handle.

    if (_server_config.staging) {
        StorServerConfigBuilder oldC(*_server_config.active);
        StorServerConfig& newC(*_server_config.staging);
        DIFFERWARN(rootFolder, "Cannot alter root folder of node live");
        DIFFERWARN(clusterName, "Cannot alter cluster name of node live");
        DIFFERWARN(nodeIndex, "Cannot alter node index of node live");
        DIFFERWARN(isDistributor, "Cannot alter role of node live");
        _server_config.active = std::make_unique<StorServerConfig>(oldC); // TODO this overwrites from ServiceLayerNode
        _server_config.staging.reset();
        _deadLockDetector->enableWarning(server_config().enableDeadLockDetectorWarnings);
        _deadLockDetector->enableShutdown(server_config().enableDeadLockDetector);
        _deadLockDetector->setProcessSlack(vespalib::from_s(server_config().deadLockDetectorTimeoutSlack));
        _deadLockDetector->setWaitSlack(vespalib::from_s(server_config().deadLockDetectorTimeoutSlack));
    }
    if (_distribution_config.staging) {
        StorDistributionConfigBuilder oldC(*_distribution_config.active);
        StorDistributionConfig& newC(*_distribution_config.staging);
        bool updated = false;
        if (DIFFER(redundancy)) {
            LOG(info, "Live config update: Altering redundancy from %u to %u.", oldC.redundancy, newC.redundancy);
            ASSIGN(redundancy);
        }
        if (DIFFER(initialRedundancy)) {
            LOG(info, "Live config update: Altering initial redundancy from %u to %u.",
                oldC.initialRedundancy, newC.initialRedundancy);
            ASSIGN(initialRedundancy);
        }
        if (DIFFER(ensurePrimaryPersisted)) {
            LOG(info, "Live config update: Now%s requiring primary copy to succeed for n of m operation to succeed.",
                newC.ensurePrimaryPersisted ? "" : " not");
            ASSIGN(ensurePrimaryPersisted);
        }
        if (DIFFER(activePerLeafGroup)) {
            LOG(info, "Live config update: Active per leaf group setting altered from %s to %s",
                oldC.activePerLeafGroup ? "true" : "false",
                newC.activePerLeafGroup ? "true" : "false");
            ASSIGN(activePerLeafGroup);
        }
        if (DIFFER(readyCopies)) {
            LOG(info, "Live config update: Altering number of searchable copies from %u to %u",
                oldC.readyCopies, newC.readyCopies);
            ASSIGN(readyCopies);
        }
        if (DIFFER(group)) {
            LOG(info, "Live config update: Group structure altered.");
            ASSIGN(group);
        }
        // This looks weird, but the magical ASSIGN() macro mutates `oldC` in-place upon changes
        _distribution_config.active = std::make_unique<StorDistributionConfig>(oldC);
        _distribution_config.staging.reset();
        if (updated) {
            _context.getComponentRegister().setDistribution(make_shared<lib::Distribution>(oldC));
            for (StorageLink* link = _chain.get(); link != nullptr; link = link->getNextLink()) {
                link->storageDistributionChanged();
            }
        }
    }

    if (_bucket_spaces_config.staging) {
        _bucket_spaces_config.promote_staging_to_active();
        _context.getComponentRegister().setBucketSpacesConfig(bucket_spaces_config());
        _communicationManager->updateBucketSpacesConfig(bucket_spaces_config());
    }
    if (_comm_mgr_config.staging) {
        _comm_mgr_config.promote_staging_to_active();
        _communicationManager->on_configure(communication_manager_config());
    }
    if (_bouncer_config.staging) {
        _bouncer_config.promote_staging_to_active();
        on_bouncer_config_changed();
    }
}

void
StorageNode::notifyDoneInitializing()
{
    bool isDistributor = (getNodeType() == lib::NodeType::DISTRIBUTOR);
    LOG(info, "%s node ready. Done initializing. Giving out of sequence metric event. Config id is %s",
        isDistributor ? "Distributor" : "Storage", _configUri.getConfigId().c_str());
    _context.getComponentRegister().getMetricManager().forceEventLogging();
    if (!_singleThreadedDebugMode) {
        EV_STARTED(isDistributor ? "distributor" : "storagenode");
    }

    NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());
    ns.setState(lib::State::UP);
    _component->getStateUpdater().setReportedNodeState(ns);
    _chain->doneInit();
}

StorageNode::~StorageNode() = default;

void
StorageNode::shutdown()
{
    // Try to shut down in opposite order of initialize. Bear in mind that
    // we might be shutting down after init exception causing only parts
    // of the server to have been initialized
    LOG(debug, "Shutting down storage node of type %s", getNodeType().toString().c_str());
    if (!attemptedStopped()) {
        LOG(debug, "Storage killed before requestShutdown() was called. No "
                   "reason has been given for why we're stopping.");
    }

    if (_chain) {
        LOG(debug, "Closing storage chain");
        _chain->close();
        LOG(debug, "Flushing storage chain");
        _chain->flush();
    }

    if ( !_pidFile.empty() ) {
        LOG(debug, "Removing pid file");
        removePidFile(_pidFile);
    }

    if (!_singleThreadedDebugMode) {
        EV_STOPPING(getNodeType() == lib::NodeType::DISTRIBUTOR ? "distributor" : "storagenode", "Stopped");
    }

    if (_context.getComponentRegister().hasMetricManager()) {
        LOG(debug, "Stopping metric manager. (Deleting chain may remove metrics)");
        _context.getComponentRegister().getMetricManager().stop();
    }

    // Delete the status web server before the actual status providers, to
    // ensure that web server does not query providers during shutdown
    _statusWebServer.reset();

    // For this to be safe, no-one can touch the state updater after we start
    // deleting the storage chain
    LOG(debug, "Removing state updater pointer as we're about to delete it.");
    if (_chain) {
        LOG(debug, "Deleting storage chain");
        _chain.reset();
    }
    if (_statusMetrics) {
        LOG(debug, "Deleting status metrics consumer");
        _statusMetrics.reset();
    }
    if (_stateReporter) {
        LOG(debug, "Deleting state reporter");
        _stateReporter.reset();
    }
    if (_stateManager) {
        LOG(debug, "Deleting state manager");
        _stateManager.reset();
    }
    if (_deadLockDetector) {
        LOG(debug, "Deleting dead lock detector");
        _deadLockDetector.reset();
    }
    if (_metricManager) {
        LOG(debug, "Deleting metric manager");
        _metricManager.reset();
    }
    if (_metrics) {
        LOG(debug, "Deleting metric set");
        _metrics.reset();
    }
    if (_component) {
        LOG(debug, "Deleting component");
        _component.reset();
    }

    LOG(debug, "Done shutting down node");
}

void
StorageNode::configure(std::unique_ptr<StorServerConfig> config) {
    stage_config_change(_server_config, std::move(config));
}

void
StorageNode::configure(std::unique_ptr<StorDistributionConfig> config) {
    stage_config_change(_distribution_config, std::move(config));
}

void
StorageNode::configure(std::unique_ptr<BucketspacesConfig> config) {
    stage_config_change(_bucket_spaces_config, std::move(config));
}

void
StorageNode::configure(std::unique_ptr<CommunicationManagerConfig> config) {
    stage_config_change(_comm_mgr_config, std::move(config));
}

void
StorageNode::configure(std::unique_ptr<StorBouncerConfig> config) {
    stage_config_change(_bouncer_config, std::move(config));
}

template <typename ConfigT>
void
StorageNode::stage_config_change(ConfigWrapper<ConfigT>& cfg, std::unique_ptr<ConfigT> new_cfg) {
    log_config_received(*new_cfg);
    // When we get config, we try to grab the config lock to ensure no one
    // else is doing configuration work, and then we write the new config
    // to a variable where we can find it later when processing config
    // updates
    {
        std::lock_guard config_lock_guard(_configLock);
        cfg.staging = std::move(new_cfg);
    }
    if (cfg.active) {
        InitialGuard concurrent_config_guard(_initial_config_mutex);
        handleLiveConfigUpdate(concurrent_config_guard);
    }
}

bool
StorageNode::attemptedStopped() const
{
    return _attemptedStopped.load(std::memory_order_relaxed);
}

void
StorageNode::updateMetrics(const MetricLockGuard &) {
    _metrics->updateMetrics();
}

void
StorageNode::waitUntilInitialized(vespalib::duration timeout) {
    vespalib::steady_time doom = vespalib::steady_clock::now() + timeout;
    while (true) {
        {
            NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
            lib::NodeState nodeState(*_component->getStateUpdater().getReportedNodeState());
            if (nodeState.getState() == lib::State::UP) break;
        }
        std::this_thread::sleep_for(10ms);
        if (vespalib::steady_clock::now() >= doom) {
            std::ostringstream ost;
            ost << "Storage server not initialized after waiting timeout of " << timeout << " seconds.";
            throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
        }
    }
}

void
StorageNode::requestShutdown(vespalib::stringref reason)
{
    bool was_stopped = false;
    const bool stop_now = _attemptedStopped.compare_exchange_strong(was_stopped, true,
                                                                    std::memory_order_relaxed, std::memory_order_relaxed);
    if (!stop_now) {
        return; // Someone else beat us to it.
    }
    if (_component) {
        NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
        lib::NodeState nodeState(*_component->getStateUpdater().getReportedNodeState());
        if (nodeState.getState() != lib::State::STOPPING) {
            nodeState.setState(lib::State::STOPPING);
            nodeState.setDescription(reason);
            _component->getStateUpdater().setReportedNodeState(nodeState);
        }
    }
}

std::unique_ptr<StateManager>
StorageNode::releaseStateManager() {
    return std::move(_stateManager);
}

void
StorageNode::set_storage_chain_builder(std::unique_ptr<IStorageChainBuilder> builder)
{
    _chain_builder = std::move(builder);
}

template <typename ConfigT>
StorageNode::ConfigWrapper<ConfigT>::ConfigWrapper() noexcept = default;

template <typename ConfigT>
StorageNode::ConfigWrapper<ConfigT>::ConfigWrapper(std::unique_ptr<ConfigT> initial_active) noexcept
    : staging(),
      active(std::move(initial_active))
{
}

template <typename ConfigT>
StorageNode::ConfigWrapper<ConfigT>::~ConfigWrapper() = default;

template <typename ConfigT>
void StorageNode::ConfigWrapper<ConfigT>::promote_staging_to_active() noexcept {
    assert(staging);
    active = std::move(staging);
}

} // storage
