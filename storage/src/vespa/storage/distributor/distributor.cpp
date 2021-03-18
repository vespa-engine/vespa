// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "blockingoperationstarter.h"
#include "distributor.h"
#include "distributor_bucket_space.h"
#include "distributor_status.h"
#include "distributor_stripe.h"
#include "distributormetricsset.h"
#include "idealstatemetricsset.h"
#include "operation_sequencer.h"
#include "ownership_transfer_safe_time_point_calculator.h"
#include "throttlingoperationstarter.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/nodestateupdater.h>
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
 *  - need a DistributorComponent per stripe
 *    - or better, remove entirely!
 *    - probably also DistributorInterface since it's used to send
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
                         bool manageActiveBucketCopies,
                         HostInfo& hostInfoReporterRegistrar,
                         ChainedMessageSender* messageSender)
    : StorageLink("distributor"),
      DistributorInterface(),
      framework::StatusReporter("distributor", "Distributor"),
      _metrics(std::make_shared<DistributorMetricSet>()),
      _messageSender(messageSender),
      _stripe(std::make_unique<DistributorStripe>(compReg, *_metrics, node_identity, threadPool, doneInitHandler,
                                                  manageActiveBucketCopies, this)),
      // TODO STRIPE remove once DistributorComponent no longer references bucket space repos
      _bucketSpaceRepo(std::make_unique<DistributorBucketSpaceRepo>(node_identity.node_index())),
      _readOnlyBucketSpaceRepo(std::make_unique<DistributorBucketSpaceRepo>(node_identity.node_index())),
      // TODO STRIPE slim down
      _component(*this, *_bucketSpaceRepo, *_readOnlyBucketSpaceRepo, compReg, "distributor"),
      _distributorStatusDelegate(compReg, *this, *this),
      _threadPool(threadPool),
      _tickResult(framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN),
      _metricUpdateHook(*this),
      _metricLock(),
      _hostInfoReporter(*this, *this)
{
    _component.registerMetric(*_metrics);
    _component.registerMetricUpdateHook(_metricUpdateHook, framework::SecondTime(0));
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

int
Distributor::getDistributorIndex() const {
    return _component.getIndex();
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

storage::distributor::DistributorComponent&
Distributor::distributor_component() noexcept {
    // TODO STRIPE We need to grab the stripe's component since tests like to access
    //             these things uncomfortably directly.
    return _stripe->_component;
}

BucketDBUpdater&
Distributor::bucket_db_updater() {
    return _stripe->bucket_db_updater();
}

const BucketDBUpdater&
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

bool Distributor::initializing() const {
    return _stripe->initializing();
}

const lib::ClusterState*
Distributor::pendingClusterStateOrNull(const document::BucketSpace& space) const {
    return bucket_db_updater().pendingClusterStateOrNull(space);
}

void
Distributor::sendCommand(const std::shared_ptr<api::StorageCommand>&)
{
    assert(false); // TODO STRIPE
}

void
Distributor::sendReply(const std::shared_ptr<api::StorageReply>&)
{
    assert(false); // TODO STRIPE
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
    _stripe->open();
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

void Distributor::send_shutdown_abort_reply(const std::shared_ptr<api::StorageMessage>& msg) {
    api::StorageReply::UP reply(
            std::dynamic_pointer_cast<api::StorageCommand>(msg)->makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Distributor is shutting down"));
    sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
}

void Distributor::onClose() {
    LOG(debug, "Distributor::onClose invoked");
    _stripe->close();
}

void Distributor::send_up_without_tracking(const std::shared_ptr<api::StorageMessage>&) {
    assert(false);
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

bool
Distributor::onDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    return _stripe->onDown(msg);
}

void
Distributor::handleCompletedMerge(
        const std::shared_ptr<api::MergeBucketReply>&)
{
    assert(false);
}

bool
Distributor::isMaintenanceReply(const api::StorageReply&) const
{
    assert(false);
}

bool
Distributor::handleReply(const std::shared_ptr<api::StorageReply>& reply)
{
    return _stripe->handleReply(reply);
}

bool
Distributor::handleMessage(const std::shared_ptr<api::StorageMessage>& msg)
{
    return _stripe->handleMessage(msg);
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

OperationRoutingSnapshot Distributor::read_snapshot_for_bucket(const document::Bucket&) const {
    abort();
}

void
Distributor::notifyDistributionChangeEnabled()
{
    _stripe->notifyDistributionChangeEnabled();
}

void
Distributor::storageDistributionChanged()
{
    // May happen from any thread.
    _stripe->storageDistributionChanged();
}

void
Distributor::recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket) {
    bucket_db_updater().recheckBucketInfo(nodeIdx, bucket);
}

namespace {

class SplitChecker : public PendingMessageTracker::Checker
{
public:
    bool found;
    uint8_t maxPri;

    SplitChecker(uint8_t maxP) : found(false), maxPri(maxP) {};

    bool check(uint32_t msgType, uint16_t node, uint8_t pri) override {
        (void) node;
        (void) pri;
        if (msgType == api::MessageType::SPLITBUCKET_ID && pri <= maxPri) {
            found = true;
            return false;
        }

        return true;
    }
};

}

void
Distributor::checkBucketForSplit(document::BucketSpace,
                                 const BucketDatabase::Entry&,
                                 uint8_t)
{
    assert(false);
}

void
Distributor::enableNextDistribution()
{
    _stripe->enableNextDistribution();
}

// TODO STRIPE only used by tests to directly inject new distribution config
void
Distributor::propagateDefaultDistribution(
        std::shared_ptr<const lib::Distribution> distribution)
{
    _stripe->propagateDefaultDistribution(std::move(distribution));
}

void
Distributor::propagateClusterStates()
{
    assert(false);
}

void
Distributor::signalWorkWasDone()
{
    _tickResult = framework::ThreadWaitInfo::MORE_WORK_ENQUEUED;
}

bool
Distributor::workWasDone() const noexcept
{
    return !_tickResult.waitWanted();
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

void Distributor::maybe_update_bucket_db_memory_usage_stats() {
    assert(false);
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
    // Propagates any new configs down to stripe(s)
    enableNextConfig();
    _stripe->doCriticalTick(idx);
    _tickResult.merge(_stripe->_tickResult);
    return _tickResult;
}

framework::ThreadWaitInfo
Distributor::doNonCriticalTick(framework::ThreadIndex idx)
{
    // TODO STRIPE stripes need their own thread loops!
    _stripe->doNonCriticalTick(idx);
    _tickResult = _stripe->_tickResult;
    return _tickResult;
}

void
Distributor::enableNextConfig()
{
    _hostInfoReporter.enableReporting(getConfig().getEnableHostInfoReporting());
    _stripe->enableNextConfig(); // TODO STRIPE avoid redundant call
}

void
Distributor::handleStatusRequests()
{
    assert(false);
}

vespalib::string
Distributor::getReportContentType(const framework::HttpUrlPath&) const
{
    abort();
}

std::string
Distributor::getActiveIdealStateOperations() const
{
    return _stripe->getActiveIdealStateOperations();
}

bool
Distributor::reportStatus(std::ostream&,
                          const framework::HttpUrlPath&) const
{
    abort();
}

bool
Distributor::handleStatusRequest(const DelegatedStatusRequest& request) const
{
    // TODO STRIPE need to aggregate status responses _across_ stripes..!
    return _stripe->handleStatusRequest(request);
}

}
