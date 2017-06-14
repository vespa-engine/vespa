// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernode.h"
#include "bouncer.h"
#include "bucketintegritychecker.h"
#include "communicationmanager.h"
#include "changedbucketownershiphandler.h"
#include "mergethrottler.h"
#include "opslogger.h"
#include "statemanager.h"
#include "priorityconverter.h"
#include <vespa/storage/visiting/messagebusvisitormessagesession.h>
#include <vespa/storage/visiting/visitormanager.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/bucketdb/storagebucketdbinitializer.h>
#include <vespa/storage/bucketmover/bucketmover.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <vespa/persistence/spi/exceptions.h>
#include <vespa/messagebus/rpcmessagebus.h>

#include <vespa/log/log.h>
LOG_SETUP(".node.servicelayer");

namespace storage {

ServiceLayerNode::ServiceLayerNode(
        const config::ConfigUri & configUri,
        ServiceLayerNodeContext& context,
        ApplicationGenerationFetcher& generationFetcher,
        spi::PersistenceProvider& persistenceProvider,
        const VisitorFactory::Map& externalVisitors)
    : StorageNode(configUri, context, generationFetcher, std::unique_ptr<HostInfo>(new HostInfo)),
      _context(context),
      _persistenceProvider(persistenceProvider),
      _partitions(0),
      _externalVisitors(externalVisitors),
      _fileStorManager(0),
      _init_has_been_called(false),
      _noUsablePartitionMode(false)
{
}

void ServiceLayerNode::init()
{
    assert( ! _init_has_been_called);
    _init_has_been_called = true;
    spi::Result initResult(_persistenceProvider.initialize());
    if (initResult.hasError()) {
        LOG(error, "Failed to initialize persistence provider: %s", initResult.toString().c_str());
        throw spi::HandledException("Failed provider init: " + initResult.toString(), VESPA_STRLOC);
    }

    spi::PartitionStateListResult result(
            _persistenceProvider.getPartitionStates());
    if (result.hasError()) {
        LOG(error, "Failed to get partition list from persistence provider: %s", result.toString().c_str());
        throw spi::HandledException("Failed to get partition list: " + result.toString(), VESPA_STRLOC);
    }
    _partitions = result.getList();
    if (_partitions.size() == 0) {
        LOG(error, "No partitions in persistence provider. See documentation "
                    "for your persistence provider as to how to set up "
                    "partitions in it.");
        throw spi::HandledException("No partitions in provider", VESPA_STRLOC);
    }
    try{
        initialize();
    } catch (spi::HandledException& e) {
        requestShutdown("Failed to initialize: " + e.getMessage());
        throw;
    } catch (const vespalib::NetworkSetupFailureException & e) {
        LOG(warning, "Network failure: '%s'", e.what());
        throw;
    } catch (const vespalib::Exception & e) {
        LOG(error, "Caught exception %s during startup. Calling destruct functions in hopes of dying gracefully.",
            e.getMessage().c_str());
        requestShutdown("Failed to initialize: " + e.getMessage());
        throw;
    }
}

ServiceLayerNode::~ServiceLayerNode()
{
    assert(_init_has_been_called);
    shutdown();
}

void
ServiceLayerNode::subscribeToConfigs()
{
    StorageNode::subscribeToConfigs();
    _configFetcher.reset(new config::ConfigFetcher(_configUri.getContext()));
    _configFetcher->subscribe<vespa::config::storage::StorDevicesConfig>(_configUri.getConfigId(), this);

    vespalib::LockGuard configLockGuard(_configLock);
    _deviceConfig = std::move(_newDevicesConfig);
        // Verify and set disk count
    if (_serverConfig->diskCount != 0
        && _serverConfig->diskCount != _partitions.size())
    {
        std::ostringstream ost;
        ost << "Storage is configured to have " << _serverConfig->diskCount
            << " disks but persistence provider states it has "
            << _partitions.size() << " disks.";
        throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
    }
    _context.getComponentRegister().setDiskCount(_partitions.size());
}

void
ServiceLayerNode::removeConfigSubscriptions()
{
    StorageNode::removeConfigSubscriptions();
    _configFetcher.reset(0);
}

void
ServiceLayerNode::initializeNodeSpecific()
{
    // Give node state to mount point initialization, such that we can
    // get disk count and state of unavailable disks set in reported
    // node state.
    NodeStateUpdater::Lock::SP lock(
            _component->getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());
    ns.setDiskCount(_partitions.size());

    uint32_t usablePartitions = 0;
    for (uint32_t i = 0; i < _partitions.size(); ++i) {
        if (_partitions[i].getState() == spi::PartitionState::UP) {
            ++usablePartitions;
        } else {
            lib::DiskState diskState(lib::State::DOWN, _partitions[i].getReason());
            ns.setDiskState(i, diskState);
        }
    }

    if (usablePartitions == 0) {
        _noUsablePartitionMode = true;
        ns.setState(lib::State::DOWN);
        ns.setDescription("All partitions are down");
    }
    ns.setCapacity(_serverConfig->nodeCapacity);
    ns.setReliability(_serverConfig->nodeReliability);
    for (uint16_t i=0; i<_serverConfig->diskCapacity.size(); ++i) {
        if (i >= ns.getDiskCount()) {
            LOG(warning, "Capacity configured for partition %" PRIu64 " but only %u partitions found.",
                _serverConfig->diskCapacity.size(), ns.getDiskCount());
            continue;
        }
        lib::DiskState ds(ns.getDiskState(i));
        ds.setCapacity(_serverConfig->diskCapacity[i]);
        ns.setDiskState(i, ds);
    }
    LOG(debug, "Adjusting reported node state to include partition count and states, capacity and reliability: %s",
        ns.toString().c_str());
    _component->getStateUpdater().setReportedNodeState(ns);
}

#define DIFFER(a) (!(oldC.a == newC.a))
#define ASSIGN(a) { oldC.a = newC.a; updated = true; }
#define DIFFERWARN(a, b) \
    if (DIFFER(a)) { LOG(warning, "Live config failure: %s.", b); }

void
ServiceLayerNode::handleLiveConfigUpdate()
{
    if (_newServerConfig.get() != 0) {
        bool updated = false;
        vespa::config::content::core::StorServerConfigBuilder oldC(*_serverConfig);
        vespa::config::content::core::StorServerConfig& newC(*_newServerConfig);
        DIFFERWARN(diskCount, "Cannot alter partition count of node live");
        {
            updated = false;
            NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
            lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());
            if (DIFFER(nodeCapacity)) {
                LOG(info, "Live config update: Updating node capacity from %f to %f.",
                    oldC.nodeCapacity, newC.nodeCapacity);
                ASSIGN(nodeCapacity);
                ns.setCapacity(newC.nodeCapacity);
            }
            if (DIFFER(diskCapacity)) {
                for (uint32_t i=0; i<newC.diskCapacity.size() && i<ns.getDiskCount(); ++i) {
                    if (newC.diskCapacity[i] != oldC.diskCapacity[i]) {
                        lib::DiskState ds(ns.getDiskState(i));
                        ds.setCapacity(newC.diskCapacity[i]);
                        ns.setDiskState(i, ds);
                        LOG(info, "Live config update: Disk capacity of disk %u changed from %f to %f.",
                            i, oldC.diskCapacity[i], newC.diskCapacity[i]);
                    }
                }
                ASSIGN(diskCapacity);
            }
            if (DIFFER(nodeReliability)) {
                LOG(info, "Live config update: Node reliability changed from %u to %u.",
                    oldC.nodeReliability, newC.nodeReliability);
                ASSIGN(nodeReliability);
                ns.setReliability(newC.nodeReliability);
            }
            if (updated) {
                _serverConfig.reset(new vespa::config::content::core::StorServerConfig(oldC));
                _component->getStateUpdater().setReportedNodeState(ns);
            }
        }
    }
    StorageNode::handleLiveConfigUpdate();
}

void
ServiceLayerNode::configure(
        std::unique_ptr<vespa::config::storage::StorDevicesConfig> config)
{
        // When we get config, we try to grab the config lock to ensure noone
        // else is doing configuration work, and then we write the new config
        // to a variable where we can find it later when processing config
        // updates
    {
        vespalib::LockGuard configLockGuard(_configLock);
        _newDevicesConfig.reset(config.release());
    }
    if (_distributionConfig.get() != 0) handleLiveConfigUpdate();
}

VisitorMessageSession::UP
ServiceLayerNode::createSession(Visitor& visitor, VisitorThread& thread)
{
    MessageBusVisitorMessageSession::UP mbusSession(
            new MessageBusVisitorMessageSession(visitor, thread));
    mbus::SourceSessionParams srcParams;
    srcParams.setThrottlePolicy(mbus::IThrottlePolicy::SP());
    srcParams.setReplyHandler(*mbusSession);
    mbusSession->setSourceSession(
            _communicationManager->getMessageBus().getMessageBus().createSourceSession(srcParams));
    return VisitorMessageSession::UP(std::move(mbusSession));
}

documentapi::Priority::Value
ServiceLayerNode::toDocumentPriority(uint8_t storagePriority) const
{
    return _communicationManager->getPriorityConverter().toDocumentPriority(storagePriority);
}

StorageLink::UP
ServiceLayerNode::createChain()
{
    ServiceLayerComponentRegister& compReg(_context.getComponentRegister());
    StorageLink::UP chain;

    chain.reset(_communicationManager = new CommunicationManager(compReg, _configUri));
    chain->push_back(StorageLink::UP(new Bouncer(compReg, _configUri)));
    if (_noUsablePartitionMode) {
        /*
         * No usable partitions. Use minimal chain. Still needs to be
         * able to report state back to cluster controller.
         */
        chain->push_back(StorageLink::UP(releaseStateManager().release()));
        return chain;
    }
    chain->push_back(StorageLink::UP(new OpsLogger(compReg, _configUri)));
    chain->push_back(StorageLink::UP(new MergeThrottler(_configUri, compReg)));
    chain->push_back(StorageLink::UP(new ChangedBucketOwnershipHandler(_configUri, compReg)));
    chain->push_back(StorageLink::UP(new BucketIntegrityChecker(_configUri, compReg)));
    chain->push_back(StorageLink::UP(new bucketmover::BucketMover(_configUri, compReg)));
    chain->push_back(StorageLink::UP(new StorageBucketDBInitializer(
            _configUri, _partitions, getDoneInitializeHandler(), compReg)));
    chain->push_back(StorageLink::UP(new BucketManager(
            _configUri, _context.getComponentRegister())));
    chain->push_back(StorageLink::UP(new VisitorManager(
            _configUri, _context.getComponentRegister(),
            *this, _externalVisitors)));
    chain->push_back(StorageLink::UP(new ModifiedBucketChecker(
            _context.getComponentRegister(), _persistenceProvider,
            _configUri)));
    chain->push_back(StorageLink::UP(_fileStorManager = new FileStorManager(
            _configUri, _partitions, _persistenceProvider,
            _context.getComponentRegister())));
    chain->push_back(StorageLink::UP(releaseStateManager().release()));
    return chain;
}

ResumeGuard
ServiceLayerNode::pause()
{
    return _fileStorManager->getFileStorHandler().pause();
}

} // storage
