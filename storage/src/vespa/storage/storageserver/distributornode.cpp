// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributornode.h"
#include "bouncer.h"
#include "communicationmanager.h"
#include "opslogger.h"
#include "statemanager.h"
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/i_storage_chain_builder.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".node.distributor");

namespace storage {

DistributorNode::DistributorNode(
        const config::ConfigUri& configUri,
        DistributorNodeContext& context,
        ApplicationGenerationFetcher& generationFetcher,
        uint32_t num_distributor_stripes,
        StorageLink::UP communicationManager,
        std::unique_ptr<IStorageChainBuilder> storage_chain_builder)
    : StorageNode(configUri, context, generationFetcher,
                  std::make_unique<HostInfo>(),
                  !communicationManager ? NORMAL : SINGLE_THREADED_TEST_MODE),
      _threadPool(framework::TickingThreadPool::createDefault("distributor", 100ms, 1, 5s)),
      _stripe_pool(std::make_unique<distributor::DistributorStripePool>()),
      _context(context),
      _timestamp_mutex(),
      _timestamp_second_counter(0),
      _intra_second_pseudo_usec_counter(0),
      _num_distributor_stripes(num_distributor_stripes),
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
    _stripe_pool->stop_and_join();
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
    builder.add(std::make_unique<storage::distributor::TopLevelDistributor>
                (dcr, *_node_identity, *_threadPool, *_stripe_pool, getDoneInitializeHandler(),
                 _num_distributor_stripes,
                 stateManager->getHostInfo()));

    builder.add(std::move(stateManager));
}

api::Timestamp
DistributorNode::generate_unique_timestamp()
{
    uint64_t now_seconds = _component->getClock().getTimeInSeconds().getTime();
    std::lock_guard lock(_timestamp_mutex);
    // We explicitly handle a seemingly decreased wall clock time, as multiple threads may
    // race with each other over a second change edge. In this case, pretend an earlier
    // timestamp took place in the same second as the newest observed wall clock time.
    if (now_seconds <= _timestamp_second_counter) {
        // ... but if we're stuck too far in the past, we trigger a process restart.
        if ((_timestamp_second_counter - now_seconds) > SanityCheckMaxWallClockSecondSkew) {
            LOG(error, "Current wall clock time is more than %u seconds in the past "
                       "compared to the highest observed wall clock time (%" PRIu64 " < %" PRIu64 "). "
                       "%u timestamps were generated within this time period.",
                SanityCheckMaxWallClockSecondSkew, now_seconds,_timestamp_second_counter,
                _intra_second_pseudo_usec_counter);
            std::_Exit(65);
        }
        assert(_intra_second_pseudo_usec_counter < 999'999);
        ++_intra_second_pseudo_usec_counter;
    } else {
        _timestamp_second_counter = now_seconds;
        _intra_second_pseudo_usec_counter = 0;
    }

    return _timestamp_second_counter * 1'000'000LL + _intra_second_pseudo_usec_counter;
}

ResumeGuard
DistributorNode::pause()
{
    return ResumeGuard();
}

} // storage
