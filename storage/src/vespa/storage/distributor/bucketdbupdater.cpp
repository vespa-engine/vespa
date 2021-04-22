// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdbupdater.h"
#include "bucket_db_prune_elision.h"
#include "bucket_space_distribution_configs.h"
#include "bucket_space_distribution_context.h"
#include "distributor.h"
#include "distributor_bucket_space.h"
#include "distributormetricsset.h"
#include "simpleclusterinformation.h"
#include "stripe_access_guard.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".distributor.bucketdb.updater");

using storage::lib::Node;
using storage::lib::NodeType;
using document::BucketSpace;

namespace storage::distributor {

BucketDBUpdater::BucketDBUpdater(DistributorStripeInterface& owner, // FIXME STRIPE!
                                 DistributorMessageSender& sender,
                                 DistributorComponentRegister& comp_reg,
                                 StripeAccessor& stripe_accessor)
    : framework::StatusReporter("temp_bucketdb", "Bucket DB Updater"), // TODO STRIPE rename once duplication is removed
      _stripe_accessor(stripe_accessor),
      _active_state_bundle(lib::ClusterState()),
      _dummy_mutable_bucket_space_repo(std::make_unique<DistributorBucketSpaceRepo>(owner.getDistributorIndex())),
      _dummy_read_only_bucket_space_repo(std::make_unique<DistributorBucketSpaceRepo>(owner.getDistributorIndex())),
      _distributor_component(owner, *_dummy_mutable_bucket_space_repo, *_dummy_read_only_bucket_space_repo, comp_reg, "Bucket DB Updater"),
      _node_ctx(_distributor_component),
      _op_ctx(_distributor_component),
      _distributor_interface(_distributor_component.getDistributor()),
      _delayed_requests(),
      _sent_messages(),
      _pending_cluster_state(),
      _history(),
      _sender(sender),
      _enqueued_rechecks(),
      _outdated_nodes_map(),
      _transition_timer(_node_ctx.clock()),
      _stale_reads_enabled(false)
{
    // FIXME STRIPE top-level Distributor needs a proper way to track the current cluster state bundle!
    propagate_active_state_bundle_internally();
    bootstrap_distribution_config(_distributor_component.getDistribution());
}

BucketDBUpdater::~BucketDBUpdater() = default;

void
BucketDBUpdater::propagate_active_state_bundle_internally() {
    for (auto* repo : {_dummy_mutable_bucket_space_repo.get(), _dummy_read_only_bucket_space_repo.get()}) {
        for (auto& iter : *repo) {
            iter.second->setClusterState(_active_state_bundle.getDerivedClusterState(iter.first));
        }
    }
}

void
BucketDBUpdater::bootstrap_distribution_config(std::shared_ptr<const lib::Distribution> distribution) {
    auto global_distr = GlobalBucketSpaceDistributionConverter::convert_to_global(*distribution);
    for (auto* repo : {_dummy_mutable_bucket_space_repo.get(), _dummy_read_only_bucket_space_repo.get()}) {
        repo->get(document::FixedBucketSpaces::default_space()).setDistribution(distribution);
        repo->get(document::FixedBucketSpaces::global_space()).setDistribution(global_distr);
    }
    // TODO STRIPE do we need to bootstrap the stripes as well here? Or do they do this on their own volition?
    //   ... need to take a guard if so, so can probably not be done at ctor time..?
}

// TODO STRIPE what to do with merge guards...
// FIXME what about bucket DB replica update timestamp allocations?! Replace with u64 counter..?
//   Must at the very least ensure we use stripe-local TS generation for DB inserts...! i.e. no global TS
//   Or do we have to touch these at all here? Just defer all this via stripe interface?
void
BucketDBUpdater::flush()
{
    for (auto & entry : _sent_messages) {
        // Cannot sendDown MergeBucketReplies during flushing, since
        // all lower links have been closed
        if (entry.second._mergeReplyGuard) {
            entry.second._mergeReplyGuard->resetReply();
        }
    }
    _sent_messages.clear();
}

void
BucketDBUpdater::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "BucketDBUpdater";
}

bool
BucketDBUpdater::should_defer_state_enabling() const noexcept
{
    return stale_reads_enabled();
}

bool
BucketDBUpdater::has_pending_cluster_state() const
{
    return static_cast<bool>(_pending_cluster_state);
}

void
BucketDBUpdater::send_request_bucket_info(
        uint16_t node,
        const document::Bucket& bucket,
        const std::shared_ptr<MergeReplyGuard>& mergeReplyGuard)
{
    if (!_op_ctx.storage_node_is_up(bucket.getBucketSpace(), node)) {
        return;
    }

    std::vector<document::BucketId> buckets;
    buckets.push_back(bucket.getBucketId());

    auto msg = std::make_shared<api::RequestBucketInfoCommand>(bucket.getBucketSpace(), buckets);

    LOG(debug, "Sending request bucket info command %" PRIu64 " for bucket %s to node %u",
        msg->getMsgId(), bucket.toString().c_str(), node);

    msg->setPriority(50);
    msg->setAddress(_node_ctx.node_address(node));

    _sent_messages[msg->getMsgId()] =
        BucketRequest(node, _op_ctx.generate_unique_timestamp(),
                      bucket, mergeReplyGuard);
    _sender.sendCommand(msg);
}

void
BucketDBUpdater::remove_superfluous_buckets(
        StripeAccessGuard& guard,
        const lib::ClusterStateBundle& new_state,
        bool is_distribution_config_change)
{
    const char* up_states = _op_ctx.storage_node_up_states();
    // TODO STRIPE explicit space -> config mapping, don't get via repo
    //   ... but we need to get the current cluster state per space..!
    for (auto& elem : _op_ctx.bucket_space_repo()) {
        const auto& old_cluster_state(elem.second->getClusterState());
        const auto& new_cluster_state = new_state.getDerivedClusterState(elem.first);

        // Running a full DB sweep is expensive, so if the cluster state transition does
        // not actually indicate that buckets should possibly be removed, we elide it entirely.
        if (!is_distribution_config_change
            && db_pruning_may_be_elided(old_cluster_state, *new_cluster_state, up_states))
        {
            LOG(debug, "[bucket space '%s']: eliding DB pruning for state transition '%s' -> '%s'",
                document::FixedBucketSpaces::to_string(elem.first).data(),
                old_cluster_state.toString().c_str(), new_cluster_state->toString().c_str());
            continue;
        }
        // TODO STRIPE should we also pass old state and distr config? Must ensure we're in sync with stripe...
        //   .. but config is set synchronously via the guard upon pending state creation edge
        auto maybe_lost = guard.remove_superfluous_buckets(elem.first, *new_cluster_state, is_distribution_config_change);
        if (maybe_lost.buckets != 0) {
            LOGBM(info, "After cluster state change %s, %zu buckets no longer "
                        "have available replicas. %zu documents in these buckets will "
                        "be unavailable until nodes come back up",
                  old_cluster_state.getTextualDifference(*new_cluster_state).c_str(),
                  maybe_lost.buckets, maybe_lost.documents);
        }
        maybe_inject_simulated_db_pruning_delay();
    }
}

namespace {

void maybe_sleep_for(std::chrono::milliseconds ms) {
    if (ms.count() > 0) {
        std::this_thread::sleep_for(ms);
    }
}

}

void
BucketDBUpdater::maybe_inject_simulated_db_pruning_delay() {
    maybe_sleep_for(_op_ctx.distributor_config().simulated_db_pruning_latency());
}

void
BucketDBUpdater::maybe_inject_simulated_db_merging_delay() {
    maybe_sleep_for(_op_ctx.distributor_config().simulated_db_merging_latency());
}

void
BucketDBUpdater::ensure_transition_timer_started()
{
    // Don't overwrite start time if we're already processing a state, as
    // that will make transition times appear artificially low.
    if (!has_pending_cluster_state()) {
        _transition_timer = framework::MilliSecTimer(_node_ctx.clock());
    }
}

void
BucketDBUpdater::complete_transition_timer()
{
    _distributor_interface.getMetrics()
            .stateTransitionTime.addValue(_transition_timer.getElapsedTimeAsDouble());
}

void
BucketDBUpdater::storage_distribution_changed(const BucketSpaceDistributionConfigs& configs)
{
    ensure_transition_timer_started();

    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    // FIXME STRIPE might this cause a mismatch with the component stuff's own distribution config..?!
    guard->update_distribution_config(configs);
    remove_superfluous_buckets(*guard, _op_ctx.cluster_state_bundle(), true);

    auto clusterInfo = std::make_shared<const SimpleClusterInformation>(
            _node_ctx.node_index(),
            _op_ctx.cluster_state_bundle(),
            _op_ctx.storage_node_up_states());
    _pending_cluster_state = PendingClusterState::createForDistributionChange(
            _node_ctx.clock(),
            std::move(clusterInfo),
            _sender,
            _op_ctx.bucket_space_repo(), // TODO STRIPE cannot use!
            _op_ctx.generate_unique_timestamp()); // TODO STRIPE must ensure no stripes can generate < this
    _outdated_nodes_map = _pending_cluster_state->getOutdatedNodesMap();

    guard->set_pending_cluster_state_bundle(_pending_cluster_state->getNewClusterStateBundle());
}

void
BucketDBUpdater::reply_to_previous_pending_cluster_state_if_any()
{
    if (_pending_cluster_state.get() && _pending_cluster_state->hasCommand()) {
        _distributor_interface.getMessageSender().sendUp(
                std::make_shared<api::SetSystemStateReply>(*_pending_cluster_state->getCommand()));
    }
}

void
BucketDBUpdater::reply_to_activation_with_actual_version(
        const api::ActivateClusterStateVersionCommand& cmd,
        uint32_t actualVersion)
{
    auto reply = std::make_shared<api::ActivateClusterStateVersionReply>(cmd);
    reply->setActualVersion(actualVersion);
    _distributor_interface.getMessageSender().sendUp(reply); // TODO let API accept rvalues
}

bool
BucketDBUpdater::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    LOG(debug, "Received new cluster state %s",
        cmd->getSystemState().toString().c_str());

    const lib::ClusterStateBundle& state = cmd->getClusterStateBundle();

    if (state == _active_state_bundle) {
        return false;
    }
    ensure_transition_timer_started();
    // Separate timer since _transition_timer might span multiple pending states.
    framework::MilliSecTimer process_timer(_node_ctx.clock());

    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    guard->update_read_snapshot_before_db_pruning();
    const auto& bundle = cmd->getClusterStateBundle();
    remove_superfluous_buckets(*guard, bundle, false);
    guard->update_read_snapshot_after_db_pruning(bundle);
    reply_to_previous_pending_cluster_state_if_any();

    auto clusterInfo = std::make_shared<const SimpleClusterInformation>(
                _node_ctx.node_index(),
                _op_ctx.cluster_state_bundle(),
                _op_ctx.storage_node_up_states());
    _pending_cluster_state = PendingClusterState::createForClusterStateChange(
            _node_ctx.clock(),
            std::move(clusterInfo),
            _sender,
            _op_ctx.bucket_space_repo(), // TODO STRIPE remove
            cmd,
            _outdated_nodes_map,
            _op_ctx.generate_unique_timestamp()); // FIXME STRIPE must be atomic across all threads
    _outdated_nodes_map = _pending_cluster_state->getOutdatedNodesMap();

    _distributor_interface.getMetrics().set_cluster_state_processing_time.addValue(
            process_timer.getElapsedTimeAsDouble());

    guard->set_pending_cluster_state_bundle(_pending_cluster_state->getNewClusterStateBundle());
    if (is_pending_cluster_state_completed()) {
        process_completed_pending_cluster_state(*guard);
    }
    return true;
}

bool
BucketDBUpdater::onActivateClusterStateVersion(const std::shared_ptr<api::ActivateClusterStateVersionCommand>& cmd)
{
    if (has_pending_cluster_state() && _pending_cluster_state->isVersionedTransition()) {
        const auto pending_version = _pending_cluster_state->clusterStateVersion();
        if (pending_version == cmd->version()) {
            if (is_pending_cluster_state_completed()) {
                assert(_pending_cluster_state->isDeferred());
                auto guard = _stripe_accessor.rendezvous_and_hold_all();
                activate_pending_cluster_state(*guard);
            } else {
                LOG(error, "Received cluster state activation for pending version %u "
                           "without pending state being complete yet. This is not expected, "
                           "as no activation should be sent before all distributors have "
                           "reported that state processing is complete.", pending_version);
                reply_to_activation_with_actual_version(*cmd, 0);  // Invalid version, will cause re-send (hopefully when completed).
                return true;
            }
        } else {
            reply_to_activation_with_actual_version(*cmd, pending_version);
            return true;
        }
    } else if (should_defer_state_enabling()) {
        // Likely just a resend, but log warn for now to get a feel of how common it is.
        LOG(warning, "Received cluster state activation command for version %u, which "
                     "has no corresponding pending state. Likely resent operation.", cmd->version());
    } else {
        LOG(debug, "Received cluster state activation command for version %u, but distributor "
                   "config does not have deferred activation enabled. Treating as no-op.", cmd->version());
    }
    // Fall through to next link in call chain that cares about this message.
    return false;
}

// TODO remove entirely from this abstraction level?
BucketDBUpdater::MergeReplyGuard::~MergeReplyGuard()
{
    if (_reply) {
        _distributor_interface.handleCompletedMerge(_reply);
    }
}

bool
BucketDBUpdater::onMergeBucketReply(
        const std::shared_ptr<api::MergeBucketReply>& reply)
{
   auto replyGuard = std::make_shared<MergeReplyGuard>(_distributor_interface, reply);

   // In case the merge was unsuccessful somehow, or some nodes weren't
   // actually merged (source-only nodes?) we request the bucket info of the
   // bucket again to make sure it's ok.
   for (uint32_t i = 0; i < reply->getNodes().size(); i++) {
       send_request_bucket_info(reply->getNodes()[i].index,
                                reply->getBucket(),
                                replyGuard);
   }

   return true;
}

void
BucketDBUpdater::send_all_queued_bucket_rechecks()
{
    LOG(spam, "Sending %zu queued bucket rechecks previously received "
              "via NotifyBucketChange commands",
        _enqueued_rechecks.size());

    for (const auto & entry :_enqueued_rechecks) {
        send_request_bucket_info(entry.node, entry.bucket, std::shared_ptr<MergeReplyGuard>());
    }
    _enqueued_rechecks.clear();
}

bool sort_pred(const BucketListMerger::BucketEntry& left,
               const BucketListMerger::BucketEntry& right)
{
    return left.first < right.first;
}

bool
BucketDBUpdater::onRequestBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl)
{
    if (pending_cluster_state_accepted(repl)) {
        return true;
    }
    return process_single_bucket_info_reply(repl);
}

bool
BucketDBUpdater::pending_cluster_state_accepted(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl)
{
    if (_pending_cluster_state.get()
        && _pending_cluster_state->onRequestBucketInfoReply(repl))
    {
        if (is_pending_cluster_state_completed()) {
            auto guard = _stripe_accessor.rendezvous_and_hold_all();
            process_completed_pending_cluster_state(*guard);
        }
        return true;
    }
    LOG(spam, "Reply %s was not accepted by pending cluster state",
        repl->toString().c_str());
    return false;
}

void
BucketDBUpdater::handle_single_bucket_info_failure(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        const BucketRequest& req)
{
    LOG(debug, "Request bucket info failed towards node %d: error was %s",
        req.targetNode, repl->getResult().toString().c_str());

    if (req.bucket.getBucketId() != document::BucketId(0)) {
        framework::MilliSecTime sendTime(_node_ctx.clock());
        sendTime += framework::MilliSecTime(100);
        _delayed_requests.emplace_back(sendTime, req);
    }
}

void
BucketDBUpdater::resend_delayed_messages()
{
    if (_pending_cluster_state) {
        _pending_cluster_state->resendDelayedMessages();
    }
    if (_delayed_requests.empty()) {
        return; // Don't fetch time if not needed
    }
    framework::MilliSecTime currentTime(_node_ctx.clock());
    while (!_delayed_requests.empty()
           && currentTime >= _delayed_requests.front().first)
    {
        BucketRequest& req(_delayed_requests.front().second);
        send_request_bucket_info(req.targetNode, req.bucket, std::shared_ptr<MergeReplyGuard>());
        _delayed_requests.pop_front();
    }
}

void
BucketDBUpdater::convert_bucket_info_to_bucket_list(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        uint16_t targetNode, BucketListMerger::BucketList& newList)
{
    for (const auto & entry : repl->getBucketInfo()) {
        LOG(debug, "Received bucket information from node %u for bucket %s: %s", targetNode,
            entry._bucketId.toString().c_str(), entry._info.toString().c_str());

        newList.emplace_back(entry._bucketId, entry._info);
    }
}

void
BucketDBUpdater::merge_bucket_info_with_database(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        const BucketRequest& req)
{
    BucketListMerger::BucketList existing;
    BucketListMerger::BucketList newList;

    find_related_buckets_in_database(req.targetNode, req.bucket, existing);
    convert_bucket_info_to_bucket_list(repl, req.targetNode, newList);

    std::sort(existing.begin(), existing.end(), sort_pred);
    std::sort(newList.begin(), newList.end(), sort_pred);

    BucketListMerger merger(newList, existing, req.timestamp);
    update_database(req.bucket.getBucketSpace(), req.targetNode, merger);
}

bool
BucketDBUpdater::process_single_bucket_info_reply(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    auto iter = _sent_messages.find(repl->getMsgId());

    // Has probably been deleted for some reason earlier.
    if (iter == _sent_messages.end()) {
        return true;
    }

    BucketRequest req = iter->second;
    _sent_messages.erase(iter);

    if (!_op_ctx.storage_node_is_up(req.bucket.getBucketSpace(), req.targetNode)) {
        // Ignore replies from nodes that are down.
        return true;
    }
    if (repl->getResult().getResult() != api::ReturnCode::OK) {
        handle_single_bucket_info_failure(repl, req);
        return true;
    }
    merge_bucket_info_with_database(repl, req);
    return true;
}

void
BucketDBUpdater::add_bucket_info_for_node(
        const BucketDatabase::Entry& e,
        uint16_t node,
        BucketListMerger::BucketList& existing) const
{
    const BucketCopy* copy(e->getNode(node));
    if (copy) {
        existing.emplace_back(e.getBucketId(), copy->getBucketInfo());
    }
}

void
BucketDBUpdater::find_related_buckets_in_database(uint16_t node, const document::Bucket& bucket,
                                                  BucketListMerger::BucketList& existing)
{
    auto &distributorBucketSpace(_op_ctx.bucket_space_repo().get(bucket.getBucketSpace()));
    std::vector<BucketDatabase::Entry> entries;
    distributorBucketSpace.getBucketDatabase().getAll(bucket.getBucketId(), entries);

    for (const BucketDatabase::Entry & entry : entries) {
        add_bucket_info_for_node(entry, node, existing);
    }
}

void
BucketDBUpdater::update_database(document::BucketSpace bucketSpace, uint16_t node, BucketListMerger& merger)
{
    for (const document::BucketId & bucketId : merger.getRemovedEntries()) {
        document::Bucket bucket(bucketSpace, bucketId);
        _op_ctx.remove_node_from_bucket_database(bucket, node);
    }

    for (const BucketListMerger::BucketEntry& entry : merger.getAddedEntries()) {
        document::Bucket bucket(bucketSpace, entry.first);
        _op_ctx.update_bucket_database(
                bucket,
                BucketCopy(merger.getTimestamp(), node, entry.second),
                DatabaseUpdate::CREATE_IF_NONEXISTING);
    }
}

bool
BucketDBUpdater::is_pending_cluster_state_completed() const
{
    return _pending_cluster_state.get() && _pending_cluster_state->done();
}

void
BucketDBUpdater::process_completed_pending_cluster_state(StripeAccessGuard& guard)
{
    if (_pending_cluster_state->isDeferred()) {
        LOG(debug, "Deferring completion of pending cluster state version %u until explicitly activated",
            _pending_cluster_state->clusterStateVersion());
        assert(_pending_cluster_state->hasCommand()); // Deferred transitions should only ever be created by state commands.
        // Sending down SetSystemState command will reach the state manager and a reply
        // will be auto-sent back to the cluster controller in charge. Once this happens,
        // it will send an explicit activation command once all distributors have reported
        // that their pending cluster states have completed.
        // A booting distributor will treat itself as "system Up" before the state has actually
        // taken effect via activation. External operation handler will keep operations from
        // actually being scheduled until state has been activated. The external operation handler
        // needs to be explicitly aware of the case where no state has yet to be activated.
        _distributor_interface.getMessageSender().sendDown(_pending_cluster_state->getCommand());
        _pending_cluster_state->clearCommand();
        return;
    }
    // Distribution config change or non-deferred cluster state. Immediately activate
    // the pending state without being told to do so explicitly.
    activate_pending_cluster_state(guard);
}

void
BucketDBUpdater::activate_pending_cluster_state(StripeAccessGuard& guard)
{
    framework::MilliSecTimer process_timer(_node_ctx.clock());

    _pending_cluster_state->merge_into_bucket_databases(guard);
    maybe_inject_simulated_db_merging_delay();

    if (_pending_cluster_state->isVersionedTransition()) {
        LOG(debug, "Activating pending cluster state version %u", _pending_cluster_state->clusterStateVersion());
        enable_current_cluster_state_bundle_in_distributor_and_stripes(guard);
        if (_pending_cluster_state->hasCommand()) {
            _distributor_interface.getMessageSender().sendDown(_pending_cluster_state->getCommand());
        }
        add_current_state_to_cluster_state_history();
    } else {
        LOG(debug, "Activating pending distribution config");
        // TODO distribution changes cannot currently be deferred as they are not
        // initiated by the cluster controller!
        _distributor_interface.notifyDistributionChangeEnabled(); // TODO factor these two out into one func?
        guard.notify_distribution_change_enabled();
    }

    guard.update_read_snapshot_after_activation(_pending_cluster_state->getNewClusterStateBundle());
    _pending_cluster_state.reset();
    _outdated_nodes_map.clear();
    guard.clear_pending_cluster_state_bundle();
    send_all_queued_bucket_rechecks();
    complete_transition_timer();
    guard.clear_read_only_bucket_repo_databases();

    _distributor_interface.getMetrics().activate_cluster_state_processing_time.addValue(
            process_timer.getElapsedTimeAsDouble());
}

void
BucketDBUpdater::enable_current_cluster_state_bundle_in_distributor_and_stripes(StripeAccessGuard& guard)
{
    const lib::ClusterStateBundle& state = _pending_cluster_state->getNewClusterStateBundle();

    _active_state_bundle = _pending_cluster_state->getNewClusterStateBundle();
    propagate_active_state_bundle_internally();

    LOG(debug, "BucketDBUpdater finished processing state %s",
        state.getBaselineClusterState()->toString().c_str());

    // First enable the cluster state for the _top-level_ distributor component.
    _distributor_interface.enableClusterStateBundle(state);
    // And then subsequently for all underlying stripes. Technically the order doesn't matter
    // since all threads are blocked at this point.
    guard.enable_cluster_state_bundle(state);
}

void BucketDBUpdater::simulate_cluster_state_bundle_activation(const lib::ClusterStateBundle& activated_state) {
    auto guard = _stripe_accessor.rendezvous_and_hold_all();
    _distributor_interface.enableClusterStateBundle(activated_state);
    guard->enable_cluster_state_bundle(activated_state);

    _active_state_bundle = activated_state;
    propagate_active_state_bundle_internally();
}

void
BucketDBUpdater::add_current_state_to_cluster_state_history()
{
    _history.push_back(_pending_cluster_state->getSummary());

    if (_history.size() > 50) {
        _history.pop_front();
    }
}

vespalib::string
BucketDBUpdater::getReportContentType(const framework::HttpUrlPath&) const
{
    return "text/xml";
}

namespace {

const vespalib::string ALL = "all";
const vespalib::string BUCKETDB = "bucketdb";
const vespalib::string BUCKETDB_UPDATER = "Bucket Database Updater";

}

void
BucketDBUpdater::BucketRequest::print_xml_tag(vespalib::xml::XmlOutputStream &xos, const vespalib::xml::XmlAttribute &timestampAttribute) const
{
    using namespace vespalib::xml;
    xos << XmlTag("storagenode")
        << XmlAttribute("index", targetNode);
    xos << XmlAttribute("bucketspace", bucket.getBucketSpace().getId(), XmlAttribute::HEX);
    if (bucket.getBucketId().getRawId() == 0) {
        xos << XmlAttribute("bucket", ALL);
    } else {
        xos << XmlAttribute("bucket", bucket.getBucketId().getId(), XmlAttribute::HEX);
    }
    xos << timestampAttribute << XmlEndTag();
}

bool
BucketDBUpdater::reportStatus(std::ostream& out,
                              const framework::HttpUrlPath& path) const
{
    using namespace vespalib::xml;
    XmlOutputStream xos(out);
    // FIXME(vekterli): have to do this manually since we cannot inherit
    // directly from XmlStatusReporter due to data races when BucketDBUpdater
    // gets status requests directly.
    xos << XmlTag("status")
        << XmlAttribute("id", BUCKETDB)
        << XmlAttribute("name", BUCKETDB_UPDATER);
    report_xml_status(xos, path);
    xos << XmlEndTag();
    return true;
}

vespalib::string
BucketDBUpdater::report_xml_status(vespalib::xml::XmlOutputStream& xos,
                                   const framework::HttpUrlPath&) const
{
    using namespace vespalib::xml;
    xos << XmlTag("bucketdb")
        << XmlTag("systemstate_active")
        << XmlContent(_op_ctx.cluster_state_bundle().getBaselineClusterState()->toString())
        << XmlEndTag();
    if (_pending_cluster_state) {
        xos << *_pending_cluster_state;
    }
    xos << XmlTag("systemstate_history");
    for (auto i(_history.rbegin()), e(_history.rend()); i != e; ++i) {
        xos << XmlTag("change")
            << XmlAttribute("from", i->_prevClusterState)
            << XmlAttribute("to", i->_newClusterState)
            << XmlAttribute("processingtime", i->_processingTime)
            << XmlEndTag();
    }
    xos << XmlEndTag()
        << XmlTag("single_bucket_requests");
    for (const auto & entry : _sent_messages)
    {
        entry.second.print_xml_tag(xos, XmlAttribute("sendtimestamp", entry.second.timestamp));
    }
    xos << XmlEndTag()
        << XmlTag("delayed_single_bucket_requests");
    for (const auto & entry : _delayed_requests)
    {
        entry.second.print_xml_tag(xos, XmlAttribute("resendtimestamp", entry.first.getTime()));
    }
    xos << XmlEndTag() << XmlEndTag();
    return "";
}

} // distributor
