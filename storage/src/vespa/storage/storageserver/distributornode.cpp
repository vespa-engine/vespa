// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributornode.h"
#include "bouncer.h"
#include "communicationmanager.h"
#include "opslogger.h"
#include "statemanager.h"
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".node.distributor");

namespace storage {

DistributorNode::DistributorNode(
        const config::ConfigUri& configUri,
        DistributorNodeContext& context,
        ApplicationGenerationFetcher& generationFetcher,
        NeedActiveState activeState,
        StorageLink::UP communicationManager)
    : StorageNode(configUri, context, generationFetcher,
            std::unique_ptr<HostInfo>(new HostInfo()),
                  communicationManager.get() == 0 ? NORMAL
                                                  : SINGLE_THREADED_TEST_MODE),
      _threadPool(framework::TickingThreadPool::createDefault("distributor")),
      _context(context),
      _lastUniqueTimestampRequested(0),
      _uniqueTimestampCounter(0),
      _manageActiveBucketCopies(activeState == NEED_ACTIVE_BUCKET_STATES_SET),
      _retrievedCommunicationManager(std::move(communicationManager))
{
    try{
        initialize();
    } catch (const vespalib::NetworkSetupFailureException & e) {
        LOG(warning, "Network failure: '%s'", e.what());
        throw;
    } catch (const vespalib::Exception & e) {
        LOG(error, "Caught exception %s during startup. Calling destruct "
                   "functions in hopes of dying gracefully.",
            e.getMessage().c_str());
        requestShutdown("Failed to initialize: " + e.getMessage());
        shutdownDistributor();
        throw;
    }
}

DistributorNode::~DistributorNode()
{
    shutdownDistributor();
}

void
DistributorNode::shutdownDistributor()
{
    _threadPool->stop();
    shutdown();
}

void
DistributorNode::initializeNodeSpecific()
{
    _context.getComponentRegister().setTimeCalculator(*this);
}

void
DistributorNode::handleConfigChange(vespa::config::content::core::StorDistributormanagerConfig& c)
{
    framework::TickingLockGuard guard(_threadPool->freezeAllTicks());
    _context.getComponentRegister().setDistributorConfig(c);
    framework::MilliSecTime ticksWaitTime(c.ticksWaitTimeMs);
    framework::MilliSecTime maxProcessTime(c.maxProcessTimeMs);
    _threadPool->updateParametersAllThreads(
        ticksWaitTime,
        maxProcessTime,
        c.ticksBeforeWait);
}

void
DistributorNode::handleConfigChange(vespa::config::content::core::StorVisitordispatcherConfig& c)
{
    framework::TickingLockGuard guard(_threadPool->freezeAllTicks());
    _context.getComponentRegister().setVisitorConfig(c);
}

StorageLink::UP
DistributorNode::createChain()
{
    DistributorComponentRegister& dcr(_context.getComponentRegister());
    // TODO: All components in this chain should use a common thread instead of
    // each having its own configfetcher.
    StorageLink::UP chain;
    if (_retrievedCommunicationManager.get()) {
        chain = std::move(_retrievedCommunicationManager);
    } else {
        chain.reset(_communicationManager
                = new CommunicationManager(dcr, _configUri));
    }
    std::unique_ptr<StateManager> stateManager(releaseStateManager());

    chain->push_back(StorageLink::UP(new Bouncer(dcr, _configUri)));
    chain->push_back(StorageLink::UP(new OpsLogger(dcr, _configUri)));
    // Distributor instance registers a host info reporter with the state
    // manager, which is safe since the lifetime of said state manager
    // extends to the end of the process.
    chain->push_back(StorageLink::UP(
            new storage::distributor::Distributor(
                dcr, *_threadPool, getDoneInitializeHandler(),
                _manageActiveBucketCopies,
                stateManager->getHostInfo())));

    chain->push_back(StorageLink::UP(stateManager.release()));
    return chain;
}

api::Timestamp
DistributorNode::getUniqueTimestamp()
{
    uint64_t timeNow(_component->getClock().getTimeInSeconds().getTime());
    if (timeNow == _lastUniqueTimestampRequested) {
        ++_uniqueTimestampCounter;
    } else {
        if (timeNow < _lastUniqueTimestampRequested) {
            LOG(error, "Time has moved backwards, from %" PRIu64 " to %" PRIu64 ".",
                _lastUniqueTimestampRequested, timeNow);
        }
        _lastUniqueTimestampRequested = timeNow;
        _uniqueTimestampCounter = 0;
    }

    return _lastUniqueTimestampRequested * 1000000ll + _uniqueTimestampCounter;
}

ResumeGuard
DistributorNode::pause()
{
    return ResumeGuard();
}

} // storage
