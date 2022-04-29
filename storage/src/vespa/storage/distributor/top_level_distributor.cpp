// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "blockingoperationstarter.h"
#include "bucket_space_distribution_configs.h"
#include "top_level_bucket_db_updater.h"
#include "top_level_distributor.h"
#include "distributor_bucket_space.h"
#include "distributor_status.h"
#include "distributor_stripe.h"
#include "distributor_stripe_pool.h"
#include "distributor_stripe_thread.h"
#include "distributor_total_metrics.h"
#include "multi_threaded_stripe_access_guard.h"
#include "operation_sequencer.h"
#include "ownership_transfer_safe_time_point_calculator.h"
#include "throttlingoperationstarter.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/config/distributorconfiguration.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor-main");

using namespace std::chrono_literals;

namespace storage::distributor {

TopLevelDistributor::TopLevelDistributor(DistributorComponentRegister& compReg,
                                         const NodeIdentity& node_identity,
                                         framework::TickingThreadPool& threadPool,
                                         DistributorStripePool& stripe_pool,
                                         DoneInitializeHandler& done_init_handler,
                                         uint32_t num_distributor_stripes,
                                         HostInfo& hostInfoReporterRegistrar,
                                         ChainedMessageSender* messageSender)
    : StorageLink("distributor"),
      framework::StatusReporter("distributor", "Distributor"),
      _node_identity(node_identity),
      _comp_reg(compReg),
      _done_init_handler(done_init_handler),
      _done_initializing(false),
      _total_metrics(std::make_shared<DistributorTotalMetrics>(num_distributor_stripes)),
      _ideal_state_total_metrics(std::make_shared<IdealStateTotalMetrics>(num_distributor_stripes)),
      _messageSender(messageSender),
      _n_stripe_bits(0),
      _stripe_pool(stripe_pool),
      _stripes(),
      _stripe_accessor(),
      _random_stripe_gen(),
      _random_stripe_gen_mutex(),
      _message_queue(),
      _fetched_messages(),
      _component(*this, compReg, "distributor"),
      _ideal_state_component(compReg, "Ideal state manager"),
      _total_config(_component.total_distributor_config_sp()),
      _bucket_db_updater(),
      _distributorStatusDelegate(compReg, *this, *this),
      _bucket_db_status_delegate(),
      _threadPool(threadPool),
      _status_to_do(),
      _fetched_status_requests(),
      _stripe_scan_notify_mutex(),
      _stripe_scan_stats(),
      _last_host_info_send_time(),
      _host_info_send_delay(1000ms),
      _maintenance_safe_time_point(),
      _maintenance_safe_time_delay(1s),
      _tickResult(framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN),
      _metricUpdateHook(*this),
      _hostInfoReporter(*this, *this),
      _distribution(),
      _next_distribution(),
      _current_internal_config_generation(_component.internal_config_generation())
{
    _component.registerMetric(*_total_metrics);
    _ideal_state_component.registerMetric(*_ideal_state_total_metrics);
    _component.registerMetricUpdateHook(_metricUpdateHook, framework::SecondTime(0));

    assert(num_distributor_stripes == adjusted_num_stripes(num_distributor_stripes));
    _n_stripe_bits = calc_num_stripe_bits(num_distributor_stripes);
    LOG(debug, "Setting up distributor with %u stripes using %u stripe bits",
        num_distributor_stripes, _n_stripe_bits);
    _stripe_accessor = std::make_unique<MultiThreadedStripeAccessor>(_stripe_pool);
    _bucket_db_updater = std::make_unique<TopLevelBucketDBUpdater>(_component, _component,
                                                                   *this, *this,
                                                                   _component.getDistribution(),
                                                                   *_stripe_accessor,
                                                                   this);
    for (size_t i = 0; i < num_distributor_stripes; ++i) {
        _stripes.emplace_back(std::make_unique<DistributorStripe>(compReg,
                                                                  _total_metrics->stripe(i),
                                                                  _ideal_state_total_metrics->stripe(i),
                                                                  node_identity,
                                                                  *this, *this,
                                                                  _done_initializing, i));
    }
    _stripe_scan_stats.resize(num_distributor_stripes);
    _distributorStatusDelegate.registerStatusPage();
    _bucket_db_status_delegate = std::make_unique<StatusReporterDelegate>(compReg, *this, *_bucket_db_updater);
    _bucket_db_status_delegate->registerStatusPage();

    _hostInfoReporter.enableReporting(config().getEnableHostInfoReporting());
    hostInfoReporterRegistrar.registerReporter(&_hostInfoReporter);
    propagate_default_distribution_thread_unsafe(_component.getDistribution()); // Stripes not started yet
};

TopLevelDistributor::~TopLevelDistributor()
{
    // XXX: why is there no _component.unregisterMetricUpdateHook()?
    closeNextLink();
}

DistributorMetricSet&
TopLevelDistributor::getMetrics()
{
    return _total_metrics->bucket_db_updater_metrics();
}

void
TopLevelDistributor::setNodeStateUp()
{
    NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
    ns.setState(lib::State::UP);
    _component.getStateUpdater().setReportedNodeState(ns);
}

void
TopLevelDistributor::onOpen()
{
    LOG(debug, "Distributor::onOpen invoked");
    setNodeStateUp();
    if (_component.getDistributorConfig().startDistributorThread) {
        _threadPool.addThread(*this);
        _threadPool.start(_component.getThreadPool());
        start_stripe_pool();
    } else {
        LOG(warning, "Not starting distributor thread as it's configured to "
                     "run. Unless you are just running a test tool, this is a "
                     "fatal error.");
    }
}

void TopLevelDistributor::onClose() {
    // Note: In a running system this function is called by the main thread in StorageApp as part of shutdown.
    // The distributor and stripe thread pools are already stopped at this point.
    LOG(debug, "Distributor::onClose invoked");
    // Tests may run with multiple stripes but without threads (for determinism's sake),
    // so only try to flush stripes if a pool is running.
    // TODO STRIPE probably also need to flush when running tests to handle any explicit close-tests.
    if (_stripe_pool.stripe_count() > 0) {
        assert(_stripe_pool.is_stopped());
        for (auto& thread : _stripe_pool) {
            thread->stripe().flush_and_close();
        }
    }
    assert(_bucket_db_updater);
    _bucket_db_updater->flush();
}

void
TopLevelDistributor::start_stripe_pool()
{
    std::vector<TickableStripe*> pool_stripes;
    for (auto& stripe : _stripes) {
        pool_stripes.push_back(stripe.get());
    }
    _stripe_pool.start(pool_stripes); // If unit testing, this won't actually start any OS threads
}

void
TopLevelDistributor::sendUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (_messageSender) {
        _messageSender->sendUp(msg);
    } else {
        StorageLink::sendUp(msg);
    }
}

void
TopLevelDistributor::sendDown(const std::shared_ptr<api::StorageMessage>& msg)
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
    case api::MessageType::GETNODESTATE_ID:
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

document::BucketId
get_bucket_id_for_striping(const api::StorageMessage& msg, const DistributorNodeContext& node_ctx)
{
    if (!msg.getBucketId().isSet()) {
        // Calculate a bucket id (dependent on the message type) to dispatch the message to the correct distributor stripe.
        switch (msg.getType().getId()) {
            case api::MessageType::PUT_ID:
            case api::MessageType::UPDATE_ID:
            case api::MessageType::REMOVE_ID:
                return node_ctx.bucket_id_factory().getBucketId(dynamic_cast<const api::TestAndSetCommand&>(msg).getDocumentId());
            case api::MessageType::REQUESTBUCKETINFO_REPLY_ID:
                return dynamic_cast<const api::RequestBucketInfoReply&>(msg).super_bucket_id();
            case api::MessageType::GET_ID:
                return node_ctx.bucket_id_factory().getBucketId(dynamic_cast<const api::GetCommand&>(msg).getDocumentId());
            case api::MessageType::VISITOR_CREATE_ID:
                return dynamic_cast<const api::CreateVisitorCommand&>(msg).super_bucket_id();
            case api::MessageType::VISITOR_CREATE_REPLY_ID:
                return dynamic_cast<const api::CreateVisitorReply&>(msg).super_bucket_id();
            default:
                return msg.getBucketId();
        }
    }
    return msg.getBucketId();
}

}

uint32_t
TopLevelDistributor::random_stripe_idx()
{
    std::lock_guard lock(_random_stripe_gen_mutex);
    return _random_stripe_gen.nextUint32() % _stripes.size();
}

uint32_t
TopLevelDistributor::stripe_of_bucket_id(const document::BucketId& bucket_id, const api::StorageMessage& msg)
{
    if (!bucket_id.isSet()) {
        LOG(error, "Message (%s) has a bucket id (%s) that is not set. Cannot route to stripe",
            msg.toString(true).c_str(), bucket_id.toString().c_str());
    }
    assert(bucket_id.isSet());
    if (bucket_id.getUsedBits() < spi::BucketLimits::MinUsedBits) {
        if (msg.getType().getId() == api::MessageType::VISITOR_CREATE_ID) {
            // This message will eventually be bounced with api::ReturnCode::WRONG_DISTRIBUTION,
            // so we can just route it to a random distributor stripe.
            return random_stripe_idx();
        }
    }
    return storage::stripe_of_bucket_key(bucket_id.toKey(), _n_stripe_bits);
}

bool
TopLevelDistributor::onDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (should_be_handled_by_top_level_bucket_db_updater(*msg)) {
        dispatch_to_main_distributor_thread_queue(msg);
        return true;
    }
    auto bucket_id = get_bucket_id_for_striping(*msg, _component);
    uint32_t stripe_idx = stripe_of_bucket_id(bucket_id, *msg);
    MBUS_TRACE(msg->getTrace(), 9,
               vespalib::make_string("Distributor::onDown(): Dispatch message to stripe %u", stripe_idx));
    bool handled = _stripes[stripe_idx]->handle_or_enqueue_message(msg);
    if (handled) {
        _stripe_pool.notify_stripe_event_has_triggered(stripe_idx);
    }
    return handled;
}

const DistributorConfiguration&
TopLevelDistributor::config() const
{
    return *_total_config;
}

void
TopLevelDistributor::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd)
{
    sendUp(cmd);
}

void
TopLevelDistributor::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    sendUp(reply);
}

void
TopLevelDistributor::storageDistributionChanged()
{
    std::lock_guard guard(_distribution_mutex);
    if (!_distribution || (*_component.getDistribution() != *_distribution)) {
        LOG(debug, "Distribution changed to %s, must re-fetch bucket information",
            _component.getDistribution()->toString().c_str());
        _next_distribution = _component.getDistribution();
    } else {
        LOG(debug, "Got distribution change, but the distribution %s was the same as before: %s",
            _component.getDistribution()->toString().c_str(),
            _distribution->toString().c_str());
    }
}

void
TopLevelDistributor::enable_next_distribution_if_changed()
{
    std::lock_guard guard(_distribution_mutex);
    if (_next_distribution) {
        _distribution = _next_distribution;
        _next_distribution = std::shared_ptr<lib::Distribution>();
        auto new_configs = BucketSpaceDistributionConfigs::from_default_distribution(_distribution);
        _bucket_db_updater->storage_distribution_changed(new_configs); // Transitively updates all stripes' configs
    }
}

void
TopLevelDistributor::propagate_default_distribution_thread_unsafe(
        std::shared_ptr<const lib::Distribution> distribution)
{
    // Should only be called at ctor time, at which point the pool is not yet running.
    assert(_stripe_pool.stripe_count() == 0);
    auto new_configs = BucketSpaceDistributionConfigs::from_default_distribution(std::move(distribution));
    for (auto& stripe : _stripes) {
        stripe->update_distribution_config(new_configs);
    }
}

std::unordered_map<uint16_t, uint32_t>
TopLevelDistributor::getMinReplica() const
{
    std::unordered_map<uint16_t, uint32_t> result;
    for (const auto& stripe : _stripes) {
        merge_min_replica_stats(result, stripe->getMinReplica());
    }
    return result;
}

BucketSpacesStatsProvider::PerNodeBucketSpacesStats
TopLevelDistributor::getBucketSpacesStats() const
{
    BucketSpacesStatsProvider::PerNodeBucketSpacesStats result;
    for (const auto& stripe : _stripes) {
        merge_per_node_bucket_spaces_stats(result, stripe->getBucketSpacesStats());
    }
    return result;
}

SimpleMaintenanceScanner::PendingMaintenanceStats
TopLevelDistributor::pending_maintenance_stats() const {
    SimpleMaintenanceScanner::PendingMaintenanceStats result;
    for (const auto& stripe : _stripes) {
        result.merge(stripe->pending_maintenance_stats());
    }
    return result;
}

void
TopLevelDistributor::propagateInternalScanMetricsToExternal()
{
    for (auto &stripe : _stripes) {
        stripe->propagateInternalScanMetricsToExternal();
    }
    _total_metrics->aggregate();
    _ideal_state_total_metrics->aggregate();
}

void
TopLevelDistributor::dispatch_to_main_distributor_thread_queue(const std::shared_ptr<api::StorageMessage>& msg)
{
    MBUS_TRACE(msg->getTrace(), 9, "Distributor: Added to main thread message queue");
    framework::TickingLockGuard guard(_threadPool.freezeCriticalTicks());
    _message_queue.emplace_back(msg);
    guard.broadcast();
}

void
TopLevelDistributor::fetch_external_messages()
{
    assert(_fetched_messages.empty());
    _fetched_messages.swap(_message_queue);
}

void
TopLevelDistributor::process_fetched_external_messages()
{
    for (auto& msg : _fetched_messages) {
        MBUS_TRACE(msg->getTrace(), 9, "Distributor: Processing message in main thread");
        if (!msg->callHandler(*_bucket_db_updater, msg)) {
            MBUS_TRACE(msg->getTrace(), 9, "Distributor: Not handling it. Sending further down");
            sendDown(msg);
        }
    }
    if (!_fetched_messages.empty()) {
        _fetched_messages.clear();
        signal_work_was_done();
    }
}

framework::ThreadWaitInfo
TopLevelDistributor::doCriticalTick([[maybe_unused]] framework::ThreadIndex idx)
{
    _tickResult = framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    enable_next_distribution_if_changed();
    fetch_status_requests();
    fetch_external_messages();
    // Propagates any new configs down to stripe(s)
    enable_next_config_if_changed();
    un_inhibit_maintenance_if_safe_time_passed();

    return _tickResult;
}

framework::ThreadWaitInfo
TopLevelDistributor::doNonCriticalTick([[maybe_unused]] framework::ThreadIndex idx)
{
    _tickResult = framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    handle_status_requests();
    process_fetched_external_messages();
    send_host_info_if_appropriate();
    _bucket_db_updater->resend_delayed_messages();
    return _tickResult;
}

void
TopLevelDistributor::enable_next_config_if_changed()
{
    // Only lazily trigger a config propagation and internal update if something has _actually changed_.
    if (_component.internal_config_generation() != _current_internal_config_generation) {
        _total_config = _component.total_distributor_config_sp();
        {
            auto guard = _stripe_accessor->rendezvous_and_hold_all();
            guard->update_total_distributor_config(_component.total_distributor_config_sp());
        }
        _hostInfoReporter.enableReporting(config().getEnableHostInfoReporting());
        _maintenance_safe_time_delay = _total_config->getMaxClusterClockSkew();
        _current_internal_config_generation = _component.internal_config_generation();
    }
}

void
TopLevelDistributor::un_inhibit_maintenance_if_safe_time_passed()
{
    if (_maintenance_safe_time_point.time_since_epoch().count() != 0) {
        using TimePoint = OwnershipTransferSafeTimePointCalculator::TimePoint;
        const auto now = TimePoint(std::chrono::seconds(_component.clock().getTimeInSeconds().getTime()));
        if (now >= _maintenance_safe_time_point) {
            // Thread safe. Relaxed store is fine; stripes will eventually observe new flag status.
            for (auto& stripe : _stripes) {
                stripe->inhibit_non_activation_maintenance_operations(false);
            }
            _maintenance_safe_time_point = TimePoint{};
            LOG(debug, "Marked all stripes as no longer inhibiting non-activation maintenance operations");
        }
    }
}

void
TopLevelDistributor::notify_stripe_wants_to_send_host_info(uint16_t stripe_index)
{
    assert(_done_initializing);
    LOG(debug, "Stripe %u has signalled an intent to send host info out-of-band", stripe_index);
    std::lock_guard lock(_stripe_scan_notify_mutex);
    assert(stripe_index < _stripe_scan_stats.size());
    auto& stats = _stripe_scan_stats[stripe_index];
    stats.wants_to_send_host_info = true;
    stats.has_reported_in_at_least_once = true;
    // TODO STRIPE consider if we want to wake up distributor thread here. Will be rechecked
    //  every nth millisecond anyway. Not really an issue for out-of-band CC notifications.
}

bool
TopLevelDistributor::may_send_host_info_on_behalf_of_stripes([[maybe_unused]] std::lock_guard<std::mutex>& held_lock) noexcept
{
    bool any_stripe_wants_to_send = false;
    for (const auto& stats : _stripe_scan_stats) {
        if (!stats.has_reported_in_at_least_once) {
            // If not all stripes have reported in at least once, they have not all completed their
            // first recovery mode pass through their DBs. To avoid sending partial stats to the cluster
            // controller, we wait with sending the first out-of-band host info reply until they have all
            // reported in.
            return false;
        }
        any_stripe_wants_to_send |= stats.wants_to_send_host_info;
    }
    return any_stripe_wants_to_send;
}

void
TopLevelDistributor::send_host_info_if_appropriate()
{
    const auto now = _component.getClock().getMonotonicTime();
    std::lock_guard lock(_stripe_scan_notify_mutex);

    if (may_send_host_info_on_behalf_of_stripes(lock)) {
        if ((now - _last_host_info_send_time) >= _host_info_send_delay) {
            LOG(debug, "Sending GetNodeState replies to cluster controllers on behalf of stripes");
            _component.getStateUpdater().immediately_send_get_node_state_replies();
            _last_host_info_send_time = now;
            for (auto& stats : _stripe_scan_stats) {
                stats.wants_to_send_host_info = false;
            }
        }
    }
}

void
TopLevelDistributor::on_cluster_state_bundle_activated(const lib::ClusterStateBundle& new_bundle,
                                                       bool has_bucket_ownership_transfer)
{
    lib::Node my_node(lib::NodeType::DISTRIBUTOR, getDistributorIndex());
    if (!_done_initializing && (new_bundle.getBaselineClusterState()->getNodeState(my_node).getState() == lib::State::UP)) {
        _done_initializing = true;
        _done_init_handler.notifyDoneInitializing();
    }
    if (has_bucket_ownership_transfer && _maintenance_safe_time_delay.count() > 0) {
        OwnershipTransferSafeTimePointCalculator safe_time_calc(_maintenance_safe_time_delay);
        using TimePoint = OwnershipTransferSafeTimePointCalculator::TimePoint;
        const auto now = TimePoint(std::chrono::milliseconds(_component.getClock().getTimeInMillis().getTime()));
        _maintenance_safe_time_point = safe_time_calc.safeTimePoint(now);
        // All stripes are in a waiting pattern and will observe this on their next tick.
        // Memory visibility enforced by all stripes being held under a mutex by our caller.
        for (auto& stripe : _stripes) {
            stripe->inhibit_non_activation_maintenance_operations(true);
        }
    }
    LOG(debug, "Activated new state version in distributor: %s", new_bundle.toString().c_str());
}

void
TopLevelDistributor::fetch_status_requests()
{
    if (_fetched_status_requests.empty()) {
        _fetched_status_requests.swap(_status_to_do);
    }
}

void
TopLevelDistributor::handle_status_requests()
{
    for (auto& s : _fetched_status_requests) {
        s->getReporter().reportStatus(s->getStream(), s->getPath());
        s->notifyCompleted();
    }
    if (!_fetched_status_requests.empty()) {
        _fetched_status_requests.clear();
        signal_work_was_done();
    }
}

void
TopLevelDistributor::signal_work_was_done()
{
    _tickResult = framework::ThreadWaitInfo::MORE_WORK_ENQUEUED;
}

bool
TopLevelDistributor::work_was_done() const noexcept
{
    return !_tickResult.waitWanted();
}

vespalib::string
TopLevelDistributor::getReportContentType(const framework::HttpUrlPath& path) const
{
    if (path.hasAttribute("page")) {
        if (path.getAttribute("page") == "buckets") {
            return "text/html";
        } else {
            return "application/xml";
        }
    } else {
        return "text/html";
    }
}

bool
TopLevelDistributor::reportStatus(std::ostream& out,
                                  const framework::HttpUrlPath& path) const
{
    if (!path.hasAttribute("page") || path.getAttribute("page") == "buckets") {
        framework::PartlyHtmlStatusReporter htmlReporter(*this);
        htmlReporter.reportHtmlHeader(out, path);
        if (!path.hasAttribute("page")) {
            out << "<p>Distributor stripes: " << _stripes.size() << "</p>\n"
                << "<p>\n"
                << "<a href=\"?page=pending\">Count of pending messages to storage nodes</a><br>\n"
                << "<a href=\"?page=buckets\">List all buckets, highlight non-ideal state</a><br>\n"
                << "</p>\n";
        } else {
            auto guard = _stripe_accessor->rendezvous_and_hold_all();
            const auto& op_ctx = _component;
            for (const auto& space : op_ctx.bucket_space_states()) {
                out << "<h2>" << document::FixedBucketSpaces::to_string(space.first) << " - " << space.first << "</h2>\n";
                guard->report_bucket_db_status(space.first, out);
            }
        }
        htmlReporter.reportHtmlFooter(out, path);
    } else {
        framework::PartlyXmlStatusReporter xmlReporter(*this, out, path);
        using namespace vespalib::xml;
        std::string page(path.getAttribute("page"));

        if (page == "pending") {
            auto guard = _stripe_accessor->rendezvous_and_hold_all();
            auto stats = guard->pending_operation_stats();
            xmlReporter << XmlTag("pending")
                        << XmlAttribute("externalload", stats.external_load_operations)
                        << XmlAttribute("maintenance", stats.maintenance_operations)
                        << XmlEndTag();
        }
    }
    return true;
}

bool
TopLevelDistributor::handleStatusRequest(const DelegatedStatusRequest& request) const
{
    auto wrappedRequest = std::make_shared<DistributorStatus>(request);
    {
        framework::TickingLockGuard guard(_threadPool.freezeCriticalTicks());
        _status_to_do.push_back(wrappedRequest);
        guard.broadcast();
    }
    wrappedRequest->waitForCompletion();
    return true;
}

}
