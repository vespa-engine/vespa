// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stripe_bucket_db_updater.h"
#include "bucket_db_prune_elision.h"
#include "bucket_space_distribution_context.h"
#include "top_level_distributor.h"
#include "distributor_bucket_space.h"
#include "distributormetricsset.h"
#include "pending_bucket_space_db_transition.h"
#include "potential_data_loss_report.h"
#include "simpleclusterinformation.h"
#include "stripe_access_guard.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".distributor.stripe_bucket_db_updater");

using document::BucketSpace;
using storage::lib::Node;
using storage::lib::NodeType;
using vespalib::xml::XmlAttribute;

namespace storage::distributor {

StripeBucketDBUpdater::StripeBucketDBUpdater(const DistributorNodeContext& node_ctx,
                                             DistributorStripeOperationContext& op_ctx,
                                             DistributorStripeInterface& owner,
                                             DistributorMessageSender& sender)
    : framework::StatusReporter("bucketdb", "Bucket DB Updater"),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _distributor_interface(owner),
      _delayedRequests(),
      _sentMessages(),
      _sender(sender),
      _enqueuedRechecks(),
      _stale_reads_enabled(false),
      _active_distribution_contexts(),
      _explicit_transition_read_guard(),
      _distribution_context_mutex()
{
    for (auto& elem : _op_ctx.bucket_space_repo()) {
        _active_distribution_contexts.emplace(
                elem.first,
                BucketSpaceDistributionContext::make_not_yet_initialized(_node_ctx.node_index()));
        _explicit_transition_read_guard.emplace(elem.first, std::shared_ptr<BucketDatabase::ReadGuard>());
    }
}

StripeBucketDBUpdater::~StripeBucketDBUpdater() = default;

OperationRoutingSnapshot StripeBucketDBUpdater::read_snapshot_for_bucket(const document::Bucket& bucket) const {
    const auto bucket_space = bucket.getBucketSpace();
    std::lock_guard lock(_distribution_context_mutex);
    auto active_state_iter = _active_distribution_contexts.find(bucket_space);
    assert(active_state_iter != _active_distribution_contexts.cend());
    auto& state = *active_state_iter->second;
    if (!state.bucket_owned_in_active_state(bucket.getBucketId())) {
        return OperationRoutingSnapshot::make_not_routable_in_state(active_state_iter->second);
    }
    const bool bucket_present_in_mutable_db = state.bucket_owned_in_pending_state(bucket.getBucketId());
    if (!bucket_present_in_mutable_db && !stale_reads_enabled()) {
        return OperationRoutingSnapshot::make_not_routable_in_state(active_state_iter->second);
    }
    const auto& space_repo = bucket_present_in_mutable_db
            ? _op_ctx.bucket_space_repo()
            : _op_ctx.read_only_bucket_space_repo();
    auto existing_guard_iter = _explicit_transition_read_guard.find(bucket_space);
    assert(existing_guard_iter != _explicit_transition_read_guard.cend());
    auto db_guard = existing_guard_iter->second
            ? existing_guard_iter-> second
            : space_repo.get(bucket_space).getBucketDatabase().acquire_read_guard();
    return OperationRoutingSnapshot::make_routable_with_guard(active_state_iter->second, std::move(db_guard), space_repo);
}

void
StripeBucketDBUpdater::flush()
{
    for (auto & entry : _sentMessages) {
        // Cannot sendDown MergeBucketReplies during flushing, since
        // all lower links have been closed
        if (entry.second._mergeReplyGuard) {
            entry.second._mergeReplyGuard->resetReply();
        }
    }
    _sentMessages.clear();
}

void
StripeBucketDBUpdater::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "StripeBucketDBUpdater";
}

bool
StripeBucketDBUpdater::shouldDeferStateEnabling() const noexcept
{
    return stale_reads_enabled();
}

bool
StripeBucketDBUpdater::hasPendingClusterState() const
{
    // Defer to the repo instead of checking our own internal pending cluster state,
    // as we won't have one if the top level distributor handles this for all stripes.
    // But if we're operating in "legacy" mode with this stripe bucket DB updater as
    // the authoritative source, there should always be an internal pending cluster
    // state if the repo is tagged as having one as well.
    // Since we also set a pending cluster state bundle when triggered by a distribution
    // config change, this check also covers that case.
    return _op_ctx.bucket_space_repo().get(document::FixedBucketSpaces::default_space()).has_pending_cluster_state();
}

const lib::ClusterState*
StripeBucketDBUpdater::pendingClusterStateOrNull(const document::BucketSpace& space) const {
    auto& distr_space = _op_ctx.bucket_space_repo().get(space);
    return (distr_space.has_pending_cluster_state()
            ? &distr_space.get_pending_cluster_state()
            : nullptr);
}

void
StripeBucketDBUpdater::sendRequestBucketInfo(
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

    LOG(debug,
        "Sending request bucket info command %" PRIu64 " for "
        "bucket %s to node %u",
        msg->getMsgId(),
        bucket.toString().c_str(),
        node);

    msg->setPriority(50);
    msg->setAddress(_node_ctx.node_address(node));

    _sentMessages[msg->getMsgId()] =
        BucketRequest(node, _op_ctx.generate_unique_timestamp(),
                      bucket, mergeReplyGuard);
    _sender.sendCommand(msg);
}

void
StripeBucketDBUpdater::recheckBucketInfo(uint32_t nodeIdx,
                                   const document::Bucket& bucket)
{
    sendRequestBucketInfo(nodeIdx, bucket, std::shared_ptr<MergeReplyGuard>());
}

void
StripeBucketDBUpdater::handle_activated_cluster_state_bundle()
{
    sendAllQueuedBucketRechecks();
}

namespace {

class ReadOnlyDbMergingInserter : public BucketDatabase::MergingProcessor {
    using NewEntries = std::vector<BucketDatabase::Entry>;
    NewEntries::const_iterator _current;
    const NewEntries::const_iterator _last;
public:
    explicit ReadOnlyDbMergingInserter(const NewEntries& new_entries)
        : _current(new_entries.cbegin()),
          _last(new_entries.cend())
    {}

    Result merge(BucketDatabase::Merger& m) override {
        const uint64_t key_to_insert = m.bucket_key();
        uint64_t key_at_cursor = 0;
        while (_current != _last) {
            key_at_cursor = _current->getBucketId().toKey();
            if (key_at_cursor >= key_to_insert) {
                break;
            }
            m.insert_before_current(_current->getBucketId(), *_current);
            ++_current;
        }
        if ((_current != _last) && (key_at_cursor == key_to_insert)) {
            // If we encounter a bucket that already exists, replace value wholesale.
            // Don't try to cleverly merge replicas, as the values we currently hold
            // in the read-only DB may be stale.
            // Note that this case shouldn't really happen, since we only add previously
            // owned buckets to the read-only DB, and subsequent adds to a non-empty DB
            // can only happen for state preemptions. Since ownership is not regained
            // before a state is stable, a bucket is only added once. But we handle it
            // anyway in case this changes at some point in the future.
            m.current_entry() = *_current;
            return Result::Update;
        }
        return Result::KeepUnchanged;
    }

    void insert_remaining_at_end(BucketDatabase::TrailingInserter& inserter) override {
        for (; _current != _last; ++_current) {
            inserter.insert_at_end(_current->getBucketId(), *_current);
        }
    }
};

}

PotentialDataLossReport
StripeBucketDBUpdater::remove_superfluous_buckets(
            document::BucketSpace bucket_space,
            const lib::ClusterState& new_state,
            bool is_distribution_change)
{
    (void)is_distribution_change; // TODO remove if not needed
    const bool move_to_read_only_db = shouldDeferStateEnabling();
    const char* up_states = storage_node_up_states();

    auto& s = _op_ctx.bucket_space_repo().get(bucket_space);
    const auto& new_distribution = s.getDistribution();
    // Elision of DB sweep is done at a higher level, so we don't have to do that here.
    auto& bucket_db = s.getBucketDatabase();
    auto& read_only_db = _op_ctx.read_only_bucket_space_repo().get(bucket_space).getBucketDatabase();

    // Remove all buckets not belonging to this distributor, or
    // being on storage nodes that are no longer up.
    MergingNodeRemover proc(
            new_state,
            _node_ctx.node_index(),
            new_distribution,
            up_states,
            move_to_read_only_db);

    bucket_db.merge(proc);
    if (move_to_read_only_db) {
        ReadOnlyDbMergingInserter read_only_merger(proc.getNonOwnedEntries());
        read_only_db.merge(read_only_merger);
    }
    PotentialDataLossReport report;
    report.buckets   = proc.removed_buckets();
    report.documents = proc.removed_documents();
    return report;
}

void
StripeBucketDBUpdater::merge_entries_into_db(document::BucketSpace bucket_space,
                                             api::Timestamp gathered_at_timestamp,
                                             const lib::Distribution& distribution,
                                             const lib::ClusterState& new_state,
                                             const char* storage_up_states,
                                             const std::unordered_set<uint16_t>& outdated_nodes,
                                             const std::vector<dbtransition::Entry>& entries)
{
    auto& s = _op_ctx.bucket_space_repo().get(bucket_space);
    auto& bucket_db = s.getBucketDatabase();

    PendingBucketSpaceDbTransition::DbMerger merger(gathered_at_timestamp, distribution, new_state,
                                                    storage_up_states, outdated_nodes, entries);
    bucket_db.merge(merger);
}

void
StripeBucketDBUpdater::clearReadOnlyBucketRepoDatabases()
{
    for (auto& space : _op_ctx.read_only_bucket_space_repo()) {
        space.second->getBucketDatabase().clear();
    }
}

void StripeBucketDBUpdater::update_read_snapshot_before_db_pruning() {
    std::lock_guard lock(_distribution_context_mutex);
    for (auto& elem : _op_ctx.bucket_space_repo()) {
        // At this point, we're still operating with a distribution context _without_ a
        // pending state, i.e. anyone using the context will expect to find buckets
        // in the DB that correspond to how the database looked like prior to pruning
        // buckets from the DB. To ensure this is not violated, take a snapshot of the
        // _mutable_ DB and expose this. This snapshot only lives until we atomically
        // flip to expose a distribution context that includes the new, pending state.
        // At that point, the read-only DB is known to contain the buckets that have
        // been pruned away, so we can release the mutable DB snapshot safely.
        // TODO test for, and handle, state preemption case!
        _explicit_transition_read_guard[elem.first] = elem.second->getBucketDatabase().acquire_read_guard();
    }
}

void StripeBucketDBUpdater::update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) {
    std::lock_guard lock(_distribution_context_mutex);
    const auto old_default_state = _op_ctx.bucket_space_repo().get(
            document::FixedBucketSpaces::default_space()).cluster_state_sp();
    for (auto& elem : _op_ctx.bucket_space_repo()) {
        auto new_distribution  = elem.second->distribution_sp();
        auto old_cluster_state = elem.second->cluster_state_sp();
        auto new_cluster_state = new_state.getDerivedClusterState(elem.first);
        _active_distribution_contexts.insert_or_assign(
                elem.first,
                BucketSpaceDistributionContext::make_state_transition(
                        std::move(old_cluster_state),
                        old_default_state,
                        std::move(new_cluster_state),
                        std::move(new_distribution),
                        _node_ctx.node_index()));
        // We can now remove the explicit mutable DB snapshot, as the buckets that have been
        // pruned away are visible in the read-only DB.
        _explicit_transition_read_guard[elem.first] = std::shared_ptr<BucketDatabase::ReadGuard>();
    }
}

void StripeBucketDBUpdater::update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) {
    std::lock_guard lock(_distribution_context_mutex);
    const auto& default_cluster_state = activated_state.getDerivedClusterState(document::FixedBucketSpaces::default_space());
    for (auto& elem : _op_ctx.bucket_space_repo()) {
        auto new_distribution  = elem.second->distribution_sp();
        auto new_cluster_state = activated_state.getDerivedClusterState(elem.first);
        _active_distribution_contexts.insert_or_assign(
                elem.first,
                BucketSpaceDistributionContext::make_stable_state(
                        std::move(new_cluster_state),
                        default_cluster_state,
                        std::move(new_distribution),
                        _node_ctx.node_index()));
    }
}

StripeBucketDBUpdater::MergeReplyGuard::~MergeReplyGuard()
{
    if (_reply) {
        _distributor_interface.handleCompletedMerge(_reply);
    }
}

bool
StripeBucketDBUpdater::onMergeBucketReply(
        const std::shared_ptr<api::MergeBucketReply>& reply)
{
   auto replyGuard = std::make_shared<MergeReplyGuard>(_distributor_interface, reply);

   // In case the merge was unsuccessful somehow, or some nodes weren't
   // actually merged (source-only nodes?) we request the bucket info of the
   // bucket again to make sure it's ok.
   for (uint32_t i = 0; i < reply->getNodes().size(); i++) {
       sendRequestBucketInfo(reply->getNodes()[i].index,
                             reply->getBucket(),
                             replyGuard);
   }

   return true;
}

void
StripeBucketDBUpdater::enqueueRecheckUntilPendingStateEnabled(
        uint16_t node,
        const document::Bucket& bucket)
{
    LOG(spam,
        "DB updater has a pending cluster state, enqueuing recheck "
        "of bucket %s on node %u until state is done processing",
        bucket.toString().c_str(),
        node);
    _enqueuedRechecks.insert(EnqueuedBucketRecheck(node, bucket));
}

void
StripeBucketDBUpdater::sendAllQueuedBucketRechecks()
{
    LOG(spam,
        "Sending %zu queued bucket rechecks previously received "
        "via NotifyBucketChange commands",
        _enqueuedRechecks.size());

    for (const auto & entry :_enqueuedRechecks) {
        sendRequestBucketInfo(entry.node, entry.bucket, std::shared_ptr<MergeReplyGuard>());
    }
    _enqueuedRechecks.clear();
}

bool
StripeBucketDBUpdater::onNotifyBucketChange(
        const std::shared_ptr<api::NotifyBucketChangeCommand>& cmd)
{
    // Immediately schedule reply to ensure it is sent.
    _sender.sendReply(std::make_shared<api::NotifyBucketChangeReply>(*cmd));

    if (!cmd->getBucketInfo().valid()) {
        LOG(error,
            "Received invalid bucket info for bucket %s from notify bucket "
            "change! Not updating bucket.",
            cmd->getBucketId().toString().c_str());
        return true;
    }
    LOG(debug,
        "Received notify bucket change from node %u for bucket %s with %s.",
        cmd->getSourceIndex(),
        cmd->getBucketId().toString().c_str(),
        cmd->getBucketInfo().toString().c_str());

    if (hasPendingClusterState()) {
        enqueueRecheckUntilPendingStateEnabled(cmd->getSourceIndex(),
                                               cmd->getBucket());
    } else {
        sendRequestBucketInfo(cmd->getSourceIndex(),
                              cmd->getBucket(),
                              std::shared_ptr<MergeReplyGuard>());
    }

    return true;
}

namespace {

bool sort_pred(const BucketListMerger::BucketEntry& left,
               const BucketListMerger::BucketEntry& right) {
    return left.first < right.first;
}

}

bool
StripeBucketDBUpdater::onRequestBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    return processSingleBucketInfoReply(repl);
}

void
StripeBucketDBUpdater::handleSingleBucketInfoFailure(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        const BucketRequest& req)
{
    LOG(debug, "Request bucket info failed towards node %d: error was %s",
        req.targetNode, repl->getResult().toString().c_str());

    if (req.bucket.getBucketId() != document::BucketId(0)) {
        framework::MilliSecTime sendTime(_node_ctx.clock());
        sendTime += framework::MilliSecTime(100);
        _delayedRequests.emplace_back(sendTime, req);
    }
}

void
StripeBucketDBUpdater::resendDelayedMessages()
{
    if (_delayedRequests.empty()) {
        return; // Don't fetch time if not needed
    }
    framework::MilliSecTime currentTime(_node_ctx.clock());
    while (!_delayedRequests.empty()
           && currentTime >= _delayedRequests.front().first)
    {
        BucketRequest& req(_delayedRequests.front().second);
        sendRequestBucketInfo(req.targetNode, req.bucket, std::shared_ptr<MergeReplyGuard>());
        _delayedRequests.pop_front();
    }
}

void
StripeBucketDBUpdater::convertBucketInfoToBucketList(
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
StripeBucketDBUpdater::mergeBucketInfoWithDatabase(
        const std::shared_ptr<api::RequestBucketInfoReply>& repl,
        const BucketRequest& req)
{
    BucketListMerger::BucketList existing;
    BucketListMerger::BucketList newList;

    findRelatedBucketsInDatabase(req.targetNode, req.bucket, existing);
    convertBucketInfoToBucketList(repl, req.targetNode, newList);

    std::sort(existing.begin(), existing.end(), sort_pred);
    std::sort(newList.begin(), newList.end(), sort_pred);

    BucketListMerger merger(newList, existing, req.timestamp);
    updateDatabase(req.bucket.getBucketSpace(), req.targetNode, merger);
}

bool
StripeBucketDBUpdater::processSingleBucketInfoReply(
        const std::shared_ptr<api::RequestBucketInfoReply> & repl)
{
    auto iter = _sentMessages.find(repl->getMsgId());

    // Has probably been deleted for some reason earlier.
    if (iter == _sentMessages.end()) {
        return true;
    }

    BucketRequest req = iter->second;
    _sentMessages.erase(iter);

    if (!_op_ctx.storage_node_is_up(req.bucket.getBucketSpace(), req.targetNode)) {
        // Ignore replies from nodes that are down.
        return true;
    }
    if (repl->getResult().getResult() != api::ReturnCode::OK) {
        handleSingleBucketInfoFailure(repl, req);
        return true;
    }
    LOG(debug, "Received single bucket info reply from node %u: %s",
        req.targetNode, repl->toString(true).c_str()); // Verbose mode to include bucket info in output
    mergeBucketInfoWithDatabase(repl, req);
    return true;
}

void
StripeBucketDBUpdater::addBucketInfoForNode(
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
StripeBucketDBUpdater::findRelatedBucketsInDatabase(uint16_t node, const document::Bucket& bucket,
                                                    BucketListMerger::BucketList& existing)
{
    auto &distributorBucketSpace(_op_ctx.bucket_space_repo().get(bucket.getBucketSpace()));
    std::vector<BucketDatabase::Entry> entries;
    distributorBucketSpace.getBucketDatabase().getAll(bucket.getBucketId(), entries);

    for (const BucketDatabase::Entry & entry : entries) {
        addBucketInfoForNode(entry, node, existing);
    }
}

void
StripeBucketDBUpdater::updateDatabase(document::BucketSpace bucketSpace, uint16_t node, BucketListMerger& merger)
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

void StripeBucketDBUpdater::simulate_cluster_state_bundle_activation(const lib::ClusterStateBundle& activated_state) {
    update_read_snapshot_after_activation(activated_state);
    _distributor_interface.enableClusterStateBundle(activated_state);
}

vespalib::string
StripeBucketDBUpdater::getReportContentType(const framework::HttpUrlPath&) const
{
    return "text/xml";
}

namespace {

const vespalib::string ALL = "all";
const vespalib::string BUCKETDB = "bucketdb";
const vespalib::string BUCKETDB_UPDATER = "Bucket Database Updater";

}

void
StripeBucketDBUpdater::BucketRequest::print_xml_tag(vespalib::xml::XmlOutputStream &xos, const vespalib::xml::XmlAttribute &timestampAttribute) const
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
StripeBucketDBUpdater::reportStatus(std::ostream& out,
                              const framework::HttpUrlPath& path) const
{
    using namespace vespalib::xml;
    XmlOutputStream xos(out);
    // FIXME(vekterli): have to do this manually since we cannot inherit
    // directly from XmlStatusReporter due to data races when StripeBucketDBUpdater
    // gets status requests directly.
    xos << XmlTag("status")
        << XmlAttribute("id", BUCKETDB)
        << XmlAttribute("name", BUCKETDB_UPDATER);
    reportXmlStatus(xos, path);
    xos << XmlEndTag();
    return true;
}

vespalib::string
StripeBucketDBUpdater::reportXmlStatus(vespalib::xml::XmlOutputStream& xos,
                                 const framework::HttpUrlPath&) const
{
    using namespace vespalib::xml;
    xos << XmlTag("bucketdb")
        << XmlTag("systemstate_active")
        << XmlContent(_op_ctx.cluster_state_bundle().getBaselineClusterState()->toString())
        << XmlEndTag();
    xos << XmlTag("single_bucket_requests");
    report_single_bucket_requests(xos);
    xos << XmlEndTag()
        << XmlTag("delayed_single_bucket_requests");
    report_delayed_single_bucket_requests(xos);
    xos << XmlEndTag() << XmlEndTag();
    return "";
}

void
StripeBucketDBUpdater::report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const
{
    for (const auto& entry : _sentMessages) {
        entry.second.print_xml_tag(xos, XmlAttribute("sendtimestamp", entry.second.timestamp));
    }
}

void
StripeBucketDBUpdater::report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const
{
    for (const auto& entry : _delayedRequests) {
        entry.second.print_xml_tag(xos, XmlAttribute("resendtimestamp", entry.first.getTime()));
    }
}

StripeBucketDBUpdater::MergingNodeRemover::MergingNodeRemover(
        const lib::ClusterState& s,
        uint16_t localIndex,
        const lib::Distribution& distribution,
        const char* upStates,
        bool track_non_owned_entries)
    : _state(s),
      _available_nodes(),
      _nonOwnedBuckets(),
      _removed_buckets(0),
      _removed_documents(0),
      _localIndex(localIndex),
      _distribution(distribution),
      _upStates(upStates),
      _track_non_owned_entries(track_non_owned_entries),
      _cachedDecisionSuperbucket(UINT64_MAX),
      _cachedOwned(false)
{
    // TODO intersection of cluster state and distribution config
    const uint16_t storage_count = s.getNodeCount(lib::NodeType::STORAGE);
    _available_nodes.resize(storage_count);
    for (uint16_t i = 0; i < storage_count; ++i) {
        if (s.getNodeState(lib::Node(lib::NodeType::STORAGE, i)).getState().oneOf(_upStates)) {
            _available_nodes[i] = true;
        }
    }
}

void
StripeBucketDBUpdater::MergingNodeRemover::logRemove(const document::BucketId& bucketId, const char* msg) const
{
    LOG(spam, "Removing bucket %s: %s", bucketId.toString().c_str(), msg);
}

namespace {

uint64_t superbucket_from_id(const document::BucketId& id, uint16_t distribution_bits) noexcept {
    // The n LSBs of the bucket ID contain the superbucket number. Mask off the rest.
    return id.getRawId() & ~(UINT64_MAX << distribution_bits);
}

}

bool
StripeBucketDBUpdater::MergingNodeRemover::distributorOwnsBucket(
        const document::BucketId& bucketId) const
{
    // TODO "no distributors available" case is the same for _all_ buckets; cache once in constructor.
    // TODO "too few bits used" case can be cheaply checked without needing exception
    try {
        const auto bits = _state.getDistributionBitCount();
        const auto this_superbucket = superbucket_from_id(bucketId, bits);
        if (_cachedDecisionSuperbucket == this_superbucket) {
            if (!_cachedOwned) {
                logRemove(bucketId, "bucket now owned by another distributor (cached)");
            }
            return _cachedOwned;
        }

        uint16_t distributor = _distribution.getIdealDistributorNode(_state, bucketId, "uim");
        _cachedDecisionSuperbucket = this_superbucket;
        _cachedOwned = (distributor == _localIndex);
        if (!_cachedOwned) {
            logRemove(bucketId, "bucket now owned by another distributor");
            return false;
        }
        return true;
    } catch (lib::TooFewBucketBitsInUseException& exc) {
        logRemove(bucketId, "using too few distribution bits now");
    } catch (lib::NoDistributorsAvailableException& exc) {
        logRemove(bucketId, "no distributors are available");
    }
    return false;
}

void
StripeBucketDBUpdater::MergingNodeRemover::setCopiesInEntry(
        BucketDatabase::Entry& e,
        const std::vector<BucketCopy>& copies) const
{
    e->clear();

    std::vector<uint16_t> order =
            _distribution.getIdealStorageNodes(_state, e.getBucketId(), _upStates);

    e->addNodes(copies, order);

    LOG(spam, "Changed %s", e->toString().c_str());
}

bool
StripeBucketDBUpdater::MergingNodeRemover::has_unavailable_nodes(const storage::BucketDatabase::Entry& e) const
{
    const uint16_t n_nodes = e->getNodeCount();
    for (uint16_t i = 0; i < n_nodes; i++) {
        const uint16_t node_idx = e->getNodeRef(i).getNode();
        if (!storage_node_is_available(node_idx)) {
            return true;
        }
    }
    return false;
}

BucketDatabase::MergingProcessor::Result
StripeBucketDBUpdater::MergingNodeRemover::merge(storage::BucketDatabase::Merger& merger)
{
    document::BucketId bucketId(merger.bucket_id());
    LOG(spam, "Check for remove: bucket %s", bucketId.toString().c_str());
    if (!distributorOwnsBucket(bucketId)) {
        // TODO remove in favor of DB snapshotting
        if (_track_non_owned_entries) {
            _nonOwnedBuckets.emplace_back(merger.current_entry());
        }
        return Result::Skip;
    }
    auto& e = merger.current_entry();

    if (e->getNodeCount() == 0) { // TODO when should this edge ever trigger?
        return Result::Skip;
    }

    if (!has_unavailable_nodes(e)) {
        return Result::KeepUnchanged;
    }

    std::vector<BucketCopy> remainingCopies;
    for (uint16_t i = 0; i < e->getNodeCount(); i++) {
        const uint16_t node_idx = e->getNodeRef(i).getNode();
        if (storage_node_is_available(node_idx)) {
            remainingCopies.push_back(e->getNodeRef(i));
        }
    }

    if (remainingCopies.empty()) {
        ++_removed_buckets;
        _removed_documents += e->getHighestDocumentCount();
        return Result::Skip;
    } else {
        setCopiesInEntry(e, remainingCopies);
        return Result::Update;
    }
}

bool
StripeBucketDBUpdater::MergingNodeRemover::storage_node_is_available(uint16_t index) const noexcept
{
    return ((index < _available_nodes.size()) && _available_nodes[index]);
}

StripeBucketDBUpdater::MergingNodeRemover::~MergingNodeRemover() = default;

} // distributor
