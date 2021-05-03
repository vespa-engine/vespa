// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "blockingoperationstarter.h"
#include "bucket_space_distribution_configs.h"
#include "bucketdbupdater.h"
#include "distributor.h"
#include "distributor_bucket_space.h"
#include "distributor_status.h"
#include "distributor_stripe.h"
#include "distributormetricsset.h"
#include "idealstatemetricsset.h"
#include "legacy_single_stripe_accessor.h"
#include "operation_sequencer.h"
#include "ownership_transfer_safe_time_point_calculator.h"
#include "throttlingoperationstarter.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/config/distributorconfiguration.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor-main");

using namespace std::chrono_literals;

namespace storage::distributor {

/* TODO STRIPE
 *  - need a DistributorStripeComponent per stripe
 *    - or better, remove entirely!
 *    - probably also DistributorStripeInterface since it's used to send
 *  - metrics aggregation
 *  - host info aggregation..!!
 *    - handled if Distributor getMinReplica etc delegates to stripes?
 *      - these are already thread safe
 *  - status aggregation
 */
Distributor::Distributor(DistributorComponentRegister& compReg,
                         const NodeIdentity& node_identity,
                         framework::TickingThreadPool& threadPool,
                         DoneInitializeHandler& doneInitHandler,
                         uint32_t num_distributor_stripes,
                         HostInfo& hostInfoReporterRegistrar,
                         ChainedMessageSender* messageSender)
    : StorageLink("distributor"),
      framework::StatusReporter("distributor", "Distributor"),
      _comp_reg(compReg),
      _metrics(std::make_shared<DistributorMetricSet>()),
      _messageSender(messageSender),
      _stripe(std::make_unique<DistributorStripe>(compReg, *_metrics, node_identity, threadPool,
                                                  doneInitHandler, *this, (num_distributor_stripes == 0))),
      _stripe_accessor(std::make_unique<LegacySingleStripeAccessor>(*_stripe)),
      _component(*this, compReg, "distributor"),
      _total_config(_component.total_distributor_config_sp()),
      _bucket_db_updater(),
      _distributorStatusDelegate(compReg, *this, *this),
      _threadPool(threadPool),
      _tickResult(framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN),
      _metricUpdateHook(*this),
      _hostInfoReporter(*this, *this),
      _distribution(),
      _next_distribution(),
      _current_internal_config_generation(_component.internal_config_generation())
{
    _component.registerMetric(*_metrics);
    _component.registerMetricUpdateHook(_metricUpdateHook, framework::SecondTime(0));
    if (num_distributor_stripes > 0) {
        LOG(info, "Setting up distributor with %u stripes", num_distributor_stripes); // TODO STRIPE remove once legacy gone
        _bucket_db_updater = std::make_unique<BucketDBUpdater>(_component, _component,
                                                               *this, *this,
                                                               _component.getDistribution(),
                                                               *_stripe_accessor);
    }
    _hostInfoReporter.enableReporting(getConfig().getEnableHostInfoReporting());
    _distributorStatusDelegate.registerStatusPage();
    hostInfoReporterRegistrar.registerReporter(&_hostInfoReporter);
    propagateDefaultDistribution(_component.getDistribution());
};

Distributor::~Distributor()
{
    // XXX: why is there no _component.unregisterMetricUpdateHook()?
    closeNextLink();
}

bool
Distributor::isInRecoveryMode() const noexcept {
    return _stripe->isInRecoveryMode();
}

const PendingMessageTracker&
Distributor::getPendingMessageTracker() const {
    return _stripe->getPendingMessageTracker();
}

PendingMessageTracker&
Distributor::getPendingMessageTracker() {
    return _stripe->getPendingMessageTracker();
}

DistributorBucketSpaceRepo&
Distributor::getBucketSpaceRepo() noexcept {
    return _stripe->getBucketSpaceRepo();
}

const DistributorBucketSpaceRepo&
Distributor::getBucketSpaceRepo() const noexcept {
    return _stripe->getBucketSpaceRepo();
}

DistributorBucketSpaceRepo&
Distributor::getReadOnlyBucketSpaceRepo() noexcept {
    return _stripe->getReadOnlyBucketSpaceRepo();
}

const DistributorBucketSpaceRepo&
Distributor::getReadyOnlyBucketSpaceRepo() const noexcept {
    return _stripe->getReadOnlyBucketSpaceRepo();;
}

storage::distributor::DistributorStripeComponent&
Distributor::distributor_component() noexcept {
    // TODO STRIPE We need to grab the stripe's component since tests like to access
    //             these things uncomfortably directly.
    return _stripe->_component;
}

StripeBucketDBUpdater&
Distributor::bucket_db_updater() {
    return _stripe->bucket_db_updater();
}

const StripeBucketDBUpdater&
Distributor::bucket_db_updater() const {
    return _stripe->bucket_db_updater();
}

IdealStateManager&
Distributor::ideal_state_manager() {
    return _stripe->ideal_state_manager();
}

const IdealStateManager&
Distributor::ideal_state_manager() const {
    return _stripe->ideal_state_manager();
}

ExternalOperationHandler&
Distributor::external_operation_handler() {
    return _stripe->external_operation_handler();
}

const ExternalOperationHandler&
Distributor::external_operation_handler() const {
    return _stripe->external_operation_handler();
}

BucketDBMetricUpdater&
Distributor::bucket_db_metric_updater() const noexcept {
    return _stripe->_bucketDBMetricUpdater;
}

const DistributorConfiguration&
Distributor::getConfig() const {
    return _stripe->getConfig();
}

std::chrono::steady_clock::duration
Distributor::db_memory_sample_interval() const noexcept {
    return _stripe->db_memory_sample_interval();
}

void
Distributor::setNodeStateUp()
{
    NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
    ns.setState(lib::State::UP);
    _component.getStateUpdater().setReportedNodeState(ns);
}

void
Distributor::onOpen()
{
    LOG(debug, "Distributor::onOpen invoked");
    setNodeStateUp();
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    framework::MilliSecTime waitTime(1000);
    if (_component.getDistributorConfig().startDistributorThread) {
        _threadPool.addThread(*this);
        _threadPool.start(_component.getThreadPool());
    } else {
        LOG(warning, "Not starting distributor thread as it's configured to "
                     "run. Unless you are just running a test tool, this is a "
                     "fatal error.");
    }
}

void Distributor::onClose() {
    LOG(debug, "Distributor::onClose invoked");
    _stripe->flush_and_close();
    if (_bucket_db_updater) {
        _bucket_db_updater->flush();
    }
}

void
Distributor::sendUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (_messageSender) {
        _messageSender->sendUp(msg);
    } else {
        StorageLink::sendUp(msg);
    }
}

void
Distributor::sendDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (_messageSender) {
        _messageSender->sendDown(msg);
    } else {
        StorageLink::sendDown(msg);
    }
}

namespace {

bool should_be_handled_by_top_level_bucket_db_updater(const api::StorageMessage& msg) noexcept {
    switch (msg.getType().getId()) {
    case api::MessageType::SETSYSTEMSTATE_ID:
    case api::MessageType::ACTIVATE_CLUSTER_STATE_VERSION_ID:
        return true;
    case api::MessageType::REQUESTBUCKETINFO_REPLY_ID:
        // Top-level component should only handle replies for full bucket info fetches.
        // Bucket-specific requests should go to the stripes that sent them.
        return dynamic_cast<const api::RequestBucketInfoReply&>(msg).full_bucket_fetch();
    default:
        return false;
    }
}

}

bool
Distributor::onDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    // FIXME STRIPE this MUST be in a separate thread to enforce processing in a single thread
    //   regardless of what RPC thread (comm mgr, FRT...) this is called from!
    if (_bucket_db_updater && should_be_handled_by_top_level_bucket_db_updater(*msg)) {
        return msg->callHandler(*_bucket_db_updater, msg);
    }
    // TODO STRIPE can we route both requests and responses that are BucketCommand|Reply based on their bucket alone?
    //   that covers most operations already...
    return _stripe->handle_or_enqueue_message(msg);
}

bool
Distributor::handleReply(const std::shared_ptr<api::StorageReply>& reply)
{
    if (_bucket_db_updater && should_be_handled_by_top_level_bucket_db_updater(*reply)) {
        return reply->callHandler(*_bucket_db_updater, reply);
    }
    return _stripe->handleReply(reply);
}

// TODO STRIPE we need to reintroduce the top-level message queue...
bool
Distributor::handleMessage(const std::shared_ptr<api::StorageMessage>& msg)
{
    return _stripe->handleMessage(msg);
}

const DistributorConfiguration&
Distributor::config() const
{
    return *_total_config;
}

void
Distributor::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd)
{
    sendUp(cmd);
}

void
Distributor::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    sendUp(reply);
}

const lib::ClusterStateBundle&
Distributor::getClusterStateBundle() const
{
    // TODO STRIPE must offer a single unifying state across stripes
    return _stripe->getClusterStateBundle();
}

void
Distributor::enableClusterStateBundle(const lib::ClusterStateBundle& state)
{
    // TODO STRIPE make test injection/force-function
    _stripe->enableClusterStateBundle(state);
}

void
Distributor::storageDistributionChanged()
{
    if (_bucket_db_updater) {
        if (!_distribution || (*_component.getDistribution() != *_distribution)) {
            LOG(debug, "Distribution changed to %s, must re-fetch bucket information",
                _component.getDistribution()->toString().c_str());
            _next_distribution = _component.getDistribution(); // FIXME this is not thread safe
        } else {
            LOG(debug, "Got distribution change, but the distribution %s was the same as before: %s",
                _component.getDistribution()->toString().c_str(),
                _distribution->toString().c_str());
        }
    } else {
        // May happen from any thread.
        _stripe->storage_distribution_changed();
    }
}

void
Distributor::enableNextDistribution()
{
    if (_bucket_db_updater) {
        if (_next_distribution) {
            _distribution = _next_distribution;
            _next_distribution = std::shared_ptr<lib::Distribution>();
            auto new_configs = BucketSpaceDistributionConfigs::from_default_distribution(_distribution);
            _bucket_db_updater->storage_distribution_changed(new_configs);
        }
    } else {
        _stripe->enableNextDistribution();
    }
}

// TODO STRIPE only used by tests to directly inject new distribution config
//   - actually, also by ctor
void
Distributor::propagateDefaultDistribution(
        std::shared_ptr<const lib::Distribution> distribution)
{
    // TODO STRIPE top-level bucket DB updater
    _stripe->propagateDefaultDistribution(std::move(distribution));
}

std::unordered_map<uint16_t, uint32_t>
Distributor::getMinReplica() const
{
    // TODO STRIPE merged snapshot from all stripes
    return _stripe->getMinReplica();
}

BucketSpacesStatsProvider::PerNodeBucketSpacesStats
Distributor::getBucketSpacesStats() const
{
    // TODO STRIPE merged snapshot from all stripes
    return _stripe->getBucketSpacesStats();
}

SimpleMaintenanceScanner::PendingMaintenanceStats
Distributor::pending_maintenance_stats() const {
    // TODO STRIPE merged snapshot from all stripes
    return _stripe->pending_maintenance_stats();
}

void
Distributor::propagateInternalScanMetricsToExternal()
{
    _stripe->propagateInternalScanMetricsToExternal();
}

void
Distributor::scanAllBuckets()
{
    _stripe->scanAllBuckets();
}

framework::ThreadWaitInfo
Distributor::doCriticalTick(framework::ThreadIndex idx)
{
    _tickResult = framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    if (_bucket_db_updater) {
        enableNextDistribution();
    }
    // Propagates any new configs down to stripe(s)
    enableNextConfig();
    _stripe->doCriticalTick(idx);
    _tickResult.merge(_stripe->_tickResult);
    return _tickResult;
}

framework::ThreadWaitInfo
Distributor::doNonCriticalTick(framework::ThreadIndex idx)
{
    if (_bucket_db_updater) {
        _bucket_db_updater->resend_delayed_messages();
    }
    // TODO STRIPE stripes need their own thread loops!
    _stripe->doNonCriticalTick(idx);
    _tickResult = _stripe->_tickResult;
    return _tickResult;
}

void
Distributor::enableNextConfig() // TODO STRIPE rename to enable_next_config_if_changed()?
{
    // Only lazily trigger a config propagation and internal update if something has _actually changed_.
    if (_component.internal_config_generation() != _current_internal_config_generation) {
        if (_bucket_db_updater) {
            _total_config = _component.total_distributor_config_sp();
            auto guard = _stripe_accessor->rendezvous_and_hold_all();
            guard->update_total_distributor_config(_component.total_distributor_config_sp());
        } else {
            _stripe->update_total_distributor_config(_component.total_distributor_config_sp());
        }
        _hostInfoReporter.enableReporting(getConfig().getEnableHostInfoReporting());
        _current_internal_config_generation = _component.internal_config_generation();
    }
    if (!_bucket_db_updater) {
        // TODO STRIPE remove these once tests are fixed to trigger reconfig properly
        _hostInfoReporter.enableReporting(getConfig().getEnableHostInfoReporting());
        _stripe->enableNextConfig(); // TODO STRIPE avoid redundant call
    }
}

vespalib::string
Distributor::getReportContentType(const framework::HttpUrlPath& path) const
{
    return _stripe->getReportContentType(path);
}

std::string
Distributor::getActiveIdealStateOperations() const
{
    return _stripe->getActiveIdealStateOperations();
}

bool
Distributor::reportStatus(std::ostream& out,
                          const framework::HttpUrlPath& path) const
{
    return _stripe->reportStatus(out, path);
}

bool
Distributor::handleStatusRequest(const DelegatedStatusRequest& request) const
{
    // TODO STRIPE need to aggregate status responses _across_ stripes..!
    return _stripe->handleStatusRequest(request);
}

}
