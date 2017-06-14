// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagenode.h"
#include "bouncer.h"
#include "bucketintegritychecker.h"
#include "communicationmanager.h"
#include "mergethrottler.h"
#include "opslogger.h"
#include "statemanager.h"
#include "statereporter.h"
#include "storagemetricsset.h"

#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/bucketdb/storagebucketdbinitializer.h>
#include <vespa/storage/bucketmover/bucketmover.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storageframework/defaultimplementation/memory/prioritymemorylogic.h>
#include <vespa/storage/common/statusmetricconsumer.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/storageutil/functor.h>
#include <vespa/storage/storageutil/log.h>
#include <vespa/storage/visiting/visitormanager.h>
#include <vespa/storage/visiting/messagebusvisitormessagesession.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/metrics/metricmanager.h>
#include <fstream>
#include <sstream>

LOG_SETUP(".node.server");

namespace storage {

namespace {

    using vespalib::getLastErrorString;

    void writePidFile(const vespalib::string& pidfile)
    {
        int rv = -1;
        vespalib::string mypid = vespalib::make_string("%d\n", getpid());
        size_t lastSlash = pidfile.rfind('/');
        if (lastSlash != vespalib::string::npos) {
            vespalib::mkdir(pidfile.substr(0, lastSlash));
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

    void removePidFile(const vespalib::string& pidfile)
    {
        if (unlink(pidfile.c_str()) != 0) {
            LOG(warning, "Failed to delete pidfile '%s': %s",
                pidfile.c_str(), getLastErrorString().c_str());
        }
    }


bool
allDisksDown(const lib::NodeState &nodeState)
{
    for (uint32_t i = 0; i < nodeState.getDiskCount(); ++i) {
        if (nodeState.getDiskState(i).getState() != lib::State::DOWN)
            return false;
    }
    return true;
}


} // End of anonymous namespace

StorageNode::StorageNode(
        const config::ConfigUri & configUri,
        StorageNodeContext& context,
        ApplicationGenerationFetcher& generationFetcher,
        std::unique_ptr<HostInfo> hostInfo,
        RunMode mode)
    : _singleThreadedDebugMode(mode == SINGLE_THREADED_TEST_MODE),
      _hostInfo(std::move(hostInfo)),
      _context(context),
      _generationFetcher(generationFetcher),
      _attemptedStopped(false),
      _configUri(configUri),
      _communicationManager(0)
{
}

void
StorageNode::subscribeToConfigs()
{
    _configFetcher.reset(new config::ConfigFetcher(_configUri.getContext()));
    _configFetcher->subscribe<vespa::config::content::StorDistributionConfig>(_configUri.getConfigId(), this);
    _configFetcher->subscribe<vespa::config::content::UpgradingConfig>(_configUri.getConfigId(), this);
    _configFetcher->subscribe<vespa::config::content::core::StorServerConfig>(_configUri.getConfigId(), this);
    _configFetcher->subscribe<vespa::config::content::core::StorPrioritymappingConfig>(_configUri.getConfigId(), this);
    _configFetcher->start();

    vespalib::LockGuard configLockGuard(_configLock);
    _serverConfig = std::move(_newServerConfig);
    _clusterConfig = std::move(_newClusterConfig);
    _distributionConfig = std::move(_newDistributionConfig);
    _priorityConfig = std::move(_newPriorityConfig);
}


void
StorageNode::initialize()
{
    // Fetch configs needed first. These functions will just grab the config
    // and store them away, while having the config lock.
    subscribeToConfigs();

    _context.getMemoryManager().setMaximumMemoryUsage(
            _serverConfig->memorytouse);
    _context.getComponentRegister().registerShutdownListener(*this);
    updateUpgradeFlag(*_clusterConfig);

    // First update some basics that doesn't depend on anything else to be
    // available
    _rootFolder = _serverConfig->rootFolder;

    _context.getComponentRegister().setNodeInfo(
            _serverConfig->clusterName, getNodeType(),
            _serverConfig->nodeIndex);
    _context.getComponentRegister().setLoadTypes(
            documentapi::LoadTypeSet::SP(
                new documentapi::LoadTypeSet(_configUri)));
    _context.getComponentRegister().setBucketIdFactory(
            document::BucketIdFactory());
    _context.getComponentRegister().setDistribution(
            lib::Distribution::SP(new lib::Distribution(*_distributionConfig)));
    _context.getComponentRegister().setPriorityConfig(*_priorityConfig);

    _metrics.reset(new StorageMetricSet);
    _component.reset(new StorageComponent(
            _context.getComponentRegister(), "storagenode"));
    _component->registerMetric(*_metrics);
    if (!_context.getComponentRegister().hasMetricManager()) {
        _metricManager.reset(new metrics::MetricManager);
        _context.getComponentRegister().setMetricManager(*_metricManager);
    }
    _component->registerMetricUpdateHook(*this, framework::SecondTime(300));

    // Initializing state manager early, as others use it init time to
    // update node state according to disk count and min used bits etc.
    // Needs node type to be set right away. Needs thread pool, index and
    // dead lock detector too, but not before open()
    _stateManager.reset(new StateManager(
            _context.getComponentRegister(),
            _context.getComponentRegister().getMetricManager(),
            std::move(_hostInfo),
            _singleThreadedDebugMode));
    _context.getComponentRegister().setNodeStateUpdater(*_stateManager);

    // Create VDS root folder, in case it doesn't already exist.
    // Maybe better to rather fail if it doesn't exist, but tests
    // might break if we do that. Might alter later.
    vespalib::mkdir(_rootFolder);

    initializeNodeSpecific();

    _memoryStatusViewer.reset(new MemoryStatusViewer(
            _context.getMemoryManager(),
            _context.getComponentRegister().getMetricManager(),
            _context.getComponentRegister()));

    _statusMetrics.reset(new StatusMetricConsumer(
            _context.getComponentRegister(), _context.getComponentRegister().getMetricManager()));
    _stateReporter.reset(new StateReporter(
            _context.getComponentRegister(), _context.getComponentRegister().getMetricManager(),
            _generationFetcher));

    // Start deadlock detector
    _deadLockDetector.reset(new DeadLockDetector(
            _context.getComponentRegister()));
    _deadLockDetector->enableWarning(
            _serverConfig->enableDeadLockDetectorWarnings);
    _deadLockDetector->enableShutdown(_serverConfig->enableDeadLockDetector);
    _deadLockDetector->setProcessSlack(framework::MilliSecTime(
            static_cast<uint32_t>(
                _serverConfig->deadLockDetectorTimeoutSlack * 1000)));
    _deadLockDetector->setWaitSlack(framework::MilliSecTime(
            static_cast<uint32_t>(
                _serverConfig->deadLockDetectorTimeoutSlack * 1000)));

    _chain.reset(createChain().release());

    // Start the metric manager, such that it starts generating snapshots
    // and the like. Note that at this time, all metrics should hopefully
    // have been created, such that we don't need to pay the extra cost of
    // reinitializing metric manager often.
    _context.getComponentRegister().getMetricManager().init(_configUri, _context.getThreadPool());

    if (_chain.get() != 0) {
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
    _statusWebServer.reset(new StatusWebServer(
            _context.getComponentRegister(), _context.getComponentRegister(),
            _configUri));
}

#define DIFFER(a) (!(oldC.a == newC.a))
#define ASSIGN(a) { oldC.a = newC.a; updated = true; }
#define DIFFERWARN(a, b) \
    if (DIFFER(a)) { LOG(warning, "Live config failure: %s.", b); }

void
StorageNode::setNewDocumentRepo(const document::DocumentTypeRepo::SP& repo)
{
    vespalib::LockGuard configLockGuard(_configLock);
    _context.getComponentRegister().setDocumentTypeRepo(repo);
    if (_communicationManager != 0) {
        _communicationManager->updateMessagebusProtocol(repo);
    }
}

void
StorageNode::updateUpgradeFlag(const vespa::config::content::UpgradingConfig& config)
{
    framework::UpgradeFlags flag(framework::NO_UPGRADE_SPECIAL_HANDLING_ACTIVE);
    if (config.upgradingMajorTo) {
        flag = framework::UPGRADING_TO_MAJOR_VERSION;
    } else if (config.upgradingMinorTo) {
        flag = framework::UPGRADING_TO_MINOR_VERSION;
    } else if (config.upgradingMajorFrom) {
        flag = framework::UPGRADING_FROM_MAJOR_VERSION;
    } else if (config.upgradingMinorFrom) {
        flag = framework::UPGRADING_FROM_MINOR_VERSION;
    }
    _context.getComponentRegister().setUpgradeFlag(flag);
}

void
StorageNode::handleLiveConfigUpdate()
{
        // Make sure we don't conflict with initialize or shutdown threads.
    vespalib::LockGuard configLockGuard(_configLock);
        // If storage haven't initialized, ignore. Initialize code will handle
        // this config.
    if (_chain.get() == 0) return;
    // If we get here, initialize is done running. We have to handle changes
    // we want to handle.

    if (_newServerConfig.get() != 0) {
        bool updated = false;
        vespa::config::content::core::StorServerConfigBuilder oldC(*_serverConfig);
        vespa::config::content::core::StorServerConfig& newC(*_newServerConfig);
        DIFFERWARN(rootFolder, "Cannot alter root folder of node live");
        DIFFERWARN(clusterName, "Cannot alter cluster name of node live");
        DIFFERWARN(nodeIndex, "Cannot alter node index of node live");
        DIFFERWARN(isDistributor, "Cannot alter role of node live");
        {
            if (DIFFER(memorytouse)) {
                LOG(info, "Live config update: Memory to use changed "
                          "from %" PRId64 " to %" PRId64 ".",
                    oldC.memorytouse, newC.memorytouse);
                ASSIGN(memorytouse);
                _context.getMemoryManager().setMaximumMemoryUsage(
                        newC.memorytouse);
            }
        }
        _serverConfig.reset(new vespa::config::content::core::StorServerConfig(oldC));
        _newServerConfig.reset(0);
        (void)updated;
    }
    if (_newDistributionConfig.get() != 0) {
        vespa::config::content::StorDistributionConfigBuilder oldC(*_distributionConfig);
        vespa::config::content::StorDistributionConfig& newC(*_newDistributionConfig);
        bool updated = false;
        if (DIFFER(redundancy)) {
            LOG(info, "Live config update: Altering redundancy from %u to %u.",
                oldC.redundancy, newC.redundancy);
            ASSIGN(redundancy);
        }
        if (DIFFER(initialRedundancy)) {
            LOG(info, "Live config update: Altering initial redundancy "
                      "from %u to %u.",
                oldC.initialRedundancy, newC.initialRedundancy);
            ASSIGN(initialRedundancy);
        }
        if (DIFFER(ensurePrimaryPersisted)) {
            LOG(info, "Live config update: Now%s requiring primary copy to "
                      "succeed for n of m operation to succeed.",
                newC.ensurePrimaryPersisted ? "" : " not");
            ASSIGN(ensurePrimaryPersisted);
        }
        if (DIFFER(activePerLeafGroup)) {
            LOG(info, "Live config update: Active per leaf group setting "
                "altered from %s to %s",
                oldC.activePerLeafGroup ? "true" : "false",
                newC.activePerLeafGroup ? "true" : "false");
            ASSIGN(activePerLeafGroup);
        }
        if (DIFFER(readyCopies)) {
            LOG(info, "Live config update: Altering number of searchable "
                "copies from %u to %u",
                oldC.readyCopies, newC.readyCopies);
            ASSIGN(readyCopies);
        }
        if (DIFFER(group)) {
            LOG(info, "Live config update: Group structure altered.");
            ASSIGN(group);
        }
        if (DIFFER(diskDistribution)) {
            LOG(info, "Live config update: Disk distribution altered from "
                      "%s to %s.",
                vespa::config::content::StorDistributionConfig::getDiskDistributionName(
                    oldC.diskDistribution).c_str(),
                vespa::config::content::StorDistributionConfig::getDiskDistributionName(
                    newC.diskDistribution).c_str());
            ASSIGN(diskDistribution);
        }
        _distributionConfig.reset(new vespa::config::content::StorDistributionConfig(oldC));
        _newDistributionConfig.reset(0);
        if (updated) {
            _context.getComponentRegister().setDistribution(
                    lib::Distribution::SP(new lib::Distribution(oldC)));
            for (StorageLink* link = _chain.get(); link != 0;
                 link = link->getNextLink())
            {
                link->storageDistributionChanged();
            }
        }
    }
    if (_newClusterConfig.get() != 0) {
        updateUpgradeFlag(*_newClusterConfig);
        if (*_clusterConfig != *_newClusterConfig) {
            LOG(warning, "Live config failure: "
                         "Cannot alter cluster config of node live.");
        }
        _newClusterConfig.reset(0);
    }
    if (_newPriorityConfig.get() != 0) {
        _priorityConfig = std::move(_newPriorityConfig);
        _context.getComponentRegister().setPriorityConfig(*_priorityConfig);
    }
}

void
StorageNode::notifyDoneInitializing()
{
    bool isDistributor = (getNodeType() == lib::NodeType::DISTRIBUTOR);
    LOG(info, "%s node ready. Done initializing. Giving out of sequence "
              "metric event. Config id is %s",
        isDistributor ? "Distributor" : "Storage", _configUri.getConfigId().c_str());
    _context.getComponentRegister().getMetricManager().forceEventLogging();
    if (!_singleThreadedDebugMode) {
        EV_STARTED(isDistributor ? "distributor" : "storagenode");
    }

    NodeStateUpdater::Lock::SP lock(
            _component->getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());
    ns.setState(lib::State::UP);
    _component->getStateUpdater().setReportedNodeState(ns);
    _chain->doneInit();
}

StorageNode::~StorageNode()
{
}

void
StorageNode::removeConfigSubscriptions()
{
    LOG(debug, "Removing config subscribers");
    _configFetcher.reset(0);
}

void
StorageNode::shutdown()
{
        // Try to shut down in opposite order of initialize. Bear in mind that
        // we might be shutting down after init exception causing only parts
        // of the server to have initialize
    LOG(debug, "Shutting down storage node of type %s",
        getNodeType().toString().c_str());
    if (!_attemptedStopped) {
        LOG(warning, "Storage killed before requestShutdown() was called. No "
                     "reason has been given for why we're stopping.");
    }
        // Remove the subscription to avoid more callbacks from config
    removeConfigSubscriptions();

    if (_chain.get()) {
        LOG(debug, "Closing storage chain");
        _chain->close();
        LOG(debug, "Flushing storage chain");
        _chain->flush();
    }

    if (_pidFile != "") {
        LOG(debug, "Removing pid file");
        removePidFile(_pidFile);
    }

    if (!_singleThreadedDebugMode) {
        EV_STOPPING(getNodeType() == lib::NodeType::DISTRIBUTOR
                    ? "distributor" : "storagenode", "Stopped");
    }

    if (_context.getComponentRegister().hasMetricManager()) {
        LOG(debug, "Stopping metric manager. "
                   "(Deleting chain may remove metrics)");
        _context.getComponentRegister().getMetricManager().stop();
    }

        // Delete the status web server before the actual status providers, to
        // ensure that web server does not query providers during shutdown
    _statusWebServer.reset(0);

        // For this to be safe, noone can touch the state updater after we start
        // deleting the storage chain
    LOG(debug, "Removing state updater pointer as we're about to delete it.");
    if (_chain.get()) {
        LOG(debug, "Deleting storage chain");
        _chain.reset(0);
    }
    if (_statusMetrics.get()) {
        LOG(debug, "Deleting status metrics consumer");
        _statusMetrics.reset(0);
    }
    if (_stateReporter.get()) {
        LOG(debug, "Deleting state reporter");
        _stateReporter.reset(0);
    }
    if (_memoryStatusViewer.get()) {
        LOG(debug, "Deleting memory status viewer");
        _memoryStatusViewer.reset(0);
    }
    if (_stateManager.get()) {
        LOG(debug, "Deleting state manager");
        _stateManager.reset(0);
    }
    if (_deadLockDetector.get()) {
        LOG(debug, "Deleting dead lock detector");
        _deadLockDetector.reset(0);
    }
    if (_metricManager.get()) {
        LOG(debug, "Deleting metric manager");
        _metricManager.reset(0);
    }
    if (_metrics.get()) {
        LOG(debug, "Deleting metric set");
        _metrics.reset();
    }
    if (_component.get()) {
        LOG(debug, "Deleting component");
        _component.reset();
    }

    LOG(debug, "Done shutting down node");
}

void StorageNode::configure(std::unique_ptr<vespa::config::content::core::StorServerConfig> config)
{
        // When we get config, we try to grab the config lock to ensure noone
        // else is doing configuration work, and then we write the new config
        // to a variable where we can find it later when processing config
        // updates
    {
        vespalib::LockGuard configLockGuard(_configLock);
        _newServerConfig.reset(config.release());
    }
    if (_serverConfig.get() != 0) handleLiveConfigUpdate();
}

void
StorageNode::configure(std::unique_ptr<vespa::config::content::UpgradingConfig> config)
{
        // When we get config, we try to grab the config lock to ensure noone
        // else is doing configuration work, and then we write the new config
        // to a variable where we can find it later when processing config
        // updates
    {
        vespalib::LockGuard configLockGuard(_configLock);
        _newClusterConfig.reset(config.release());
    }
    if (_clusterConfig.get() != 0) handleLiveConfigUpdate();
}

void
StorageNode::configure(std::unique_ptr<vespa::config::content::StorDistributionConfig> config)
{
        // When we get config, we try to grab the config lock to ensure noone
        // else is doing configuration work, and then we write the new config
        // to a variable where we can find it later when processing config
        // updates
    {
        vespalib::LockGuard configLockGuard(_configLock);
        _newDistributionConfig.reset(config.release());
    }
    if (_distributionConfig.get() != 0) handleLiveConfigUpdate();
}

void
StorageNode::configure(std::unique_ptr<vespa::config::content::core::StorPrioritymappingConfig> config)
{
    {
        vespalib::LockGuard configLockGuard(_configLock);
        _newPriorityConfig.reset(config.release());
    }
    if (_priorityConfig.get() != 0) handleLiveConfigUpdate();
}

void StorageNode::configure(std::unique_ptr<document::DocumenttypesConfig> config,
                            bool hasChanged, int64_t generation)
{
    (void) generation;
    if (!hasChanged)
        return;
    {
        vespalib::LockGuard configLockGuard(_configLock);
        _newDoctypesConfig.reset(config.release());
    }
    if (_doctypesConfig.get() != 0) handleLiveConfigUpdate();
}

bool
StorageNode::attemptedStopped() const
{
    return _attemptedStopped;
}

void
StorageNode::updateMetrics(const MetricLockGuard &) {
    _metrics->updateMetrics();
}

void
StorageNode::waitUntilInitialized(uint32_t timeout) {
    framework::defaultimplementation::RealClock clock;
    framework::MilliSecTime endTime(
            clock.getTimeInMillis() + framework::MilliSecTime(1000 * timeout));
    while (true) {
        {
            NodeStateUpdater::Lock::SP lock(
                    _component->getStateUpdater().grabStateChangeLock());
            lib::NodeState nodeState(
                    *_component->getStateUpdater().getReportedNodeState());
            if (nodeState.getState() == lib::State::UP) break;
        }
        FastOS_Thread::Sleep(10);
        if (clock.getTimeInMillis() >= endTime) {
            std::ostringstream ost;
            ost << "Storage server not initialized after waiting timeout of "
                << timeout << " seconds.";
            throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
        }
    }
}

void
StorageNode::requestShutdown(vespalib::stringref reason)
{
    if (_attemptedStopped) return;
    if (_component) {
        NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
        lib::NodeState nodeState(*_component->getStateUpdater().getReportedNodeState());
        if (nodeState.getState() != lib::State::STOPPING) {
            nodeState.setState(lib::State::STOPPING);
            nodeState.setDescription(reason);
            _component->getStateUpdater().setReportedNodeState(nodeState);
        }
    }
    _attemptedStopped = true;
}


void
StorageNode::notifyPartitionDown(int partId, vespalib::stringref reason)
{
    if (!_component)
        return;
    NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
    lib::NodeState nodeState(*_component->getStateUpdater().getReportedNodeState());
    if (partId >= nodeState.getDiskCount())
        return;
    lib::DiskState diskState(nodeState.getDiskState(partId));
    if (diskState.getState() == lib::State::DOWN)
        return;
    diskState.setState(lib::State::DOWN);
    diskState.setDescription(reason);
    nodeState.setDiskState(partId, diskState);
    if (allDisksDown(nodeState)) {
        nodeState.setState(lib::State::DOWN);
        nodeState.setDescription("All partitions are down");
    }
    _component->getStateUpdater().setReportedNodeState(nodeState);
}


std::unique_ptr<StateManager>
StorageNode::releaseStateManager() {
    return std::move(_stateManager);
}

} // storage
