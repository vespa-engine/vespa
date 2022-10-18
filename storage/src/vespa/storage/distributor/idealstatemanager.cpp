// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idealstatemanager.h"
#include "statecheckers.h"
#include "top_level_distributor.h"
#include "idealstatemetricsset.h"
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/util/assert.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation.queue");

using document::BucketSpace;
using storage::lib::Node;
using storage::lib::NodeType;

namespace storage::distributor {

IdealStateManager::IdealStateManager(
        const DistributorNodeContext& node_ctx,
        DistributorStripeOperationContext& op_ctx,
        IdealStateMetricSet& metrics)
    : _metrics(metrics),
      _node_ctx(node_ctx),
      _op_ctx(op_ctx),
      _has_logged_phantom_replica_warning(false)
{
    LOG(debug, "Adding BucketStateStateChecker to state checkers");
    _stateCheckers.emplace_back(std::make_shared<BucketStateStateChecker>());

    _stateCheckers.emplace_back(std::make_shared<SplitBucketStateChecker>());
    _splitBucketStateChecker = dynamic_cast<SplitBucketStateChecker *>(_stateCheckers.back().get());

    _stateCheckers.emplace_back(std::make_shared<SplitInconsistentStateChecker>());
    _stateCheckers.emplace_back(std::make_shared<SynchronizeAndMoveStateChecker>());
    _stateCheckers.emplace_back(std::make_shared<JoinBucketsStateChecker>());
    _stateCheckers.emplace_back(std::make_shared<DeleteExtraCopiesStateChecker>());
    _stateCheckers.emplace_back(std::make_shared<GarbageCollectionStateChecker>());
}

IdealStateManager::~IdealStateManager() = default;

void
IdealStateManager::print(std::ostream& out, bool verbose, const std::string& indent)
{
    (void) verbose; (void) indent;
    out << "IdealStateManager";
}

void
IdealStateManager::fillParentAndChildBuckets(StateChecker::Context& c)
{
    c.db.getAll(c.getBucketId(), c.entries);
    if (c.entries.empty()) {
        LOG(spam,
            "Did not find bucket %s in bucket database",
            c.bucket.toString().c_str());
    }
}
void
IdealStateManager::fillSiblingBucket(StateChecker::Context& c)
{
    c.siblingEntry = c.db.get(c.siblingBucket);
}

BucketDatabase::Entry*
IdealStateManager::getEntryForPrimaryBucket(StateChecker::Context& c)
{
    for (auto & e : c.entries) {
        if (e.getBucketId() == c.getBucketId() && ! e->getNodes().empty()) {
            return &e;
        }
    }
    return nullptr;
}

namespace {

/*
 * Since state checkers are in prioritized order, don't allow
 * overwriting if already explicitly set.
 */
bool
canOverwriteResult(const StateChecker::Result& existing,
                   const StateChecker::Result& candidate)
{
    return (!existing.getPriority().requiresMaintenance()
            && candidate.getPriority().requiresMaintenance());
}

}

StateChecker::Result
IdealStateManager::runStateCheckers(StateChecker::Context& c) const
{
    auto highestPri = StateChecker::Result::noMaintenanceNeeded();
    // We go through _all_ active state checkers so that statistics can be
    // collected across all checkers, not just the ones that are highest pri.
    for (const auto & checker : _stateCheckers) {
        if (!operation_context().distributor_config().stateCheckerIsActive(
                checker->getName()))
        {
            LOG(spam, "Skipping state checker %s", checker->getName());
            continue;
        }

        auto result = checker->check(c);
        if (canOverwriteResult(highestPri, result)) {
            highestPri = std::move(result);
        }
    }
    return highestPri;
}

void IdealStateManager::verify_only_live_nodes_in_context(const StateChecker::Context& c) const {
    if (_has_logged_phantom_replica_warning) {
        return;
    }
    for (const auto& n : c.entry->getRawNodes()) {
        const uint16_t index = n.getNode();
        const auto& state = c.systemState.getNodeState(lib::Node(lib::NodeType::STORAGE, index));
        // Only nodes in Up, Initializing or Retired should ever be present in the DB.
        if (!state.getState().oneOf("uir")) {
            LOG(error, "%s in bucket DB is on node %u, which is in unavailable state %s. "
                       "Current cluster state is '%s'",
                       c.entry.getBucketId().toString().c_str(),
                       index,
                       state.getState().toString().c_str(),
                       c.systemState.toString().c_str());
            ASSERT_ONCE_OR_LOG(false, "Bucket DB contains replicas on unavailable node", 10000);
            _has_logged_phantom_replica_warning = true;
        }
    }
}

StateChecker::Result
IdealStateManager::generateHighestPriority(
        const document::Bucket &bucket,
        NodeMaintenanceStatsTracker& statsTracker) const
{
    auto& distributorBucketSpace = _op_ctx.bucket_space_repo().get(bucket.getBucketSpace());
    StateChecker::Context c(node_context(), operation_context(), distributorBucketSpace, statsTracker, bucket);
    fillParentAndChildBuckets(c);
    fillSiblingBucket(c);

    BucketDatabase::Entry* e(getEntryForPrimaryBucket(c));
    if (!e) {
        return StateChecker::Result::noMaintenanceNeeded();
    }
    LOG(spam, "Checking bucket %s", e->toString().c_str());

    c.entry = *e;
    verify_only_live_nodes_in_context(c);
    return runStateCheckers(c);
}

MaintenancePriorityAndType
IdealStateManager::prioritize(
        const document::Bucket &bucket,
        NodeMaintenanceStatsTracker& statsTracker) const
{
    StateChecker::Result generated(generateHighestPriority(bucket, statsTracker));
    MaintenancePriority priority(generated.getPriority());
    MaintenanceOperation::Type type(priority.requiresMaintenance()
                                    ? generated.getType()
                                    : MaintenanceOperation::OPERATION_COUNT);
    return {priority, type};
}

IdealStateOperation::SP
IdealStateManager::generateInterceptingSplit(BucketSpace bucketSpace,
                                             const BucketDatabase::Entry& e,
                                             api::StorageMessage::Priority pri)
{
    NodeMaintenanceStatsTracker statsTracker;
    document::Bucket bucket(bucketSpace, e.getBucketId());
    auto& distributorBucketSpace = _op_ctx.bucket_space_repo().get(bucket.getBucketSpace());
    StateChecker::Context c(node_context(), operation_context(), distributorBucketSpace, statsTracker, bucket);
    if (e.valid()) {
        c.entry = e;

        IdealStateOperation::UP operation(_splitBucketStateChecker->check(c).createOperation());
        if (operation.get()) {
            operation->setPriority(pri);
            operation->setIdealStateManager(this);
        }

        return operation;
    }

    return {};
}

MaintenanceOperation::SP
IdealStateManager::generate(const document::Bucket &bucket) const
{
    NodeMaintenanceStatsTracker statsTracker;
    IdealStateOperation::SP op(
            generateHighestPriority(bucket, statsTracker).createOperation());
    if (op.get()) {
        op->setIdealStateManager(
                const_cast<IdealStateManager*>(this));
    }
    return op;
}

std::vector<MaintenanceOperation::SP>
IdealStateManager::generateAll(const document::Bucket &bucket,
                               NodeMaintenanceStatsTracker& statsTracker) const
{
    auto& distributorBucketSpace = _op_ctx.bucket_space_repo().get(bucket.getBucketSpace());
    StateChecker::Context c(node_context(), operation_context(), distributorBucketSpace, statsTracker, bucket);
    fillParentAndChildBuckets(c);
    fillSiblingBucket(c);
    BucketDatabase::Entry* e(getEntryForPrimaryBucket(c));
    std::vector<MaintenanceOperation::SP> operations;
    if (e) {
        c.entry = *e;
    } else {
        return operations;
    }

    for (const auto & checker : _stateCheckers) {
        IdealStateOperation::UP op(checker->check(c).createOperation());
        if (op) {
            operations.emplace_back(std::move(op));
        }
    }
    return operations;
}

void
IdealStateManager::getBucketStatus(
        BucketSpace bucketSpace,
        const BucketDatabase::ConstEntryRef& entry,
        NodeMaintenanceStatsTracker& statsTracker,
        std::ostream& out) const
{
    document::Bucket bucket(bucketSpace, entry.getBucketId());
    std::vector<MaintenanceOperation::SP> operations(
            generateAll(bucket, statsTracker));
    if (operations.empty()) {
        out << entry.getBucketId() << " : ";
    } else {
        out << "<b>" << entry.getBucketId() << ":</b> <i> : ";
    }
    for (uint32_t i = 0; i < operations.size(); ++i) {
        const MaintenanceOperation& op(*operations[i]);
        if (i > 0) {
            out << ", ";
        }
        out << op.getName() << ": " << op.getDetailedReason();
    }
    if (!operations.empty()) {
        out << "</i> ";
    }
    out << "[" << entry->toString() << "]<br>\n";
}

void IdealStateManager::dump_bucket_space_db_status(document::BucketSpace bucket_space, std::ostream& out) const {
    StatusBucketVisitor proc(*this, bucket_space, out);
    auto& distributorBucketSpace = _op_ctx.bucket_space_repo().get(bucket_space);
    distributorBucketSpace.getBucketDatabase().forEach(proc);
}

void IdealStateManager::getBucketStatus(std::ostream& out) const {
    LOG(debug, "Dumping bucket database valid at cluster state version %u",
        operation_context().cluster_state_bundle().getVersion());

    for (auto& space : _op_ctx.bucket_space_repo()) {
        out << "<h2>" << document::FixedBucketSpaces::to_string(space.first) << " - " << space.first << "</h2>\n";
        dump_bucket_space_db_status(space.first, out);
    }
}

} // storage::distributor
