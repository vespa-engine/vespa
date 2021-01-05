// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributornode.h"
#include "bouncer.h"
#include "communicationmanager.h"
#include "opslogger.h"
#include "statemanager.h"
#include <vespa/storage/common/i_storage_chain_builder.h>
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
        StorageLink::UP communicationManager,
        std::unique_ptr<IStorageChainBuilder> storage_chain_builder)
    : StorageNode(configUri, context, generationFetcher,
                  std::make_unique<HostInfo>(),
                  !communicationManager ? NORMAL : SINGLE_THREADED_TEST_MODE),
      _threadPool(framework::TickingThreadPool::createDefault("distributor")),
      _context(context),
      _lastUniqueTimestampRequested(0),
      _uniqueTimestampCounter(0),
      _manageActiveBucketCopies(activeState == NEED_ACTIVE_BUCKET_STATES_SET),
      _retrievedCommunicationManager(std::move(communicationManager))
{
    if (storage_chain_builder) {
        set_storage_chain_builder(std::move(storage_chain_builder));
    }
    try {
        initialize();
    } catch (const vespalib::Exception & e) {
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
    _threadPool->updateParametersAllThreads(std::chrono::milliseconds(c.ticksWaitTimeMs),
                                            std::chrono::milliseconds(c.maxProcessTimeMs),
                                            c.ticksBeforeWait);
}

void
DistributorNode::handleConfigChange(vespa::config::content::core::StorVisitordispatcherConfig& c)
{
    framework::TickingLockGuard guard(_threadPool->freezeAllTicks());
    _context.getComponentRegister().setVisitorConfig(c);
}

void
DistributorNode::createChain(IStorageChainBuilder &builder)
{
    DistributorComponentRegister& dcr(_context.getComponentRegister());
    // TODO: All components in this chain should use a common thread instead of
    // each having its own configfetcher.
    StorageLink::UP chain;
    if (_retrievedCommunicationManager.get()) {
        builder.add(std::move(_retrievedCommunicationManager));
    } else {
        auto communication_manager = std::make_unique<CommunicationManager>(dcr, _configUri);
        _communicationManager = communication_manager.get();
        builder.add(std::move(communication_manager));
    }
    std::unique_ptr<StateManager> stateManager(releaseStateManager());

    builder.add(std::make_unique<Bouncer>(dcr, _configUri));
    builder.add(std::make_unique<OpsLogger>(dcr, _configUri));
    // Distributor instance registers a host info reporter with the state
    // manager, which is safe since the lifetime of said state manager
    // extends to the end of the process.
    builder.add(std::make_unique<storage::distributor::Distributor>
                (dcr, *_node_identity, *_threadPool, getDoneInitializeHandler(),
                 _manageActiveBucketCopies,
                 stateManager->getHostInfo()));

    builder.add(std::move(stateManager));
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
