// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idealstatemanager.h"
#include "statecheckers.h"
#include "distributor.h"
#include "idealstatemetricsset.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/storage/storageserver/storagemetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation.queue");

using storage::lib::Node;
using storage::lib::NodeType;

namespace storage {
namespace distributor {

IdealStateManager::IdealStateManager(
        Distributor& owner,
        ManagedBucketSpace& bucketSpace,
        DistributorComponentRegister& compReg,
        bool manageActiveBucketCopies)
    : HtmlStatusReporter("idealstateman", "Ideal state manager"),
      _metrics(new IdealStateMetricSet),
      _distributorComponent(owner, bucketSpace, compReg, "Ideal state manager")
{
    _distributorComponent.registerStatusPage(*this);
    _distributorComponent.registerMetric(*_metrics);

    if (manageActiveBucketCopies) {
        LOG(debug, "Adding BucketStateStateChecker to state checkers");
        _stateCheckers.push_back(
                StateChecker::SP(new BucketStateStateChecker()));
    }

    _splitBucketStateChecker = new SplitBucketStateChecker();
    _stateCheckers.push_back(StateChecker::SP(_splitBucketStateChecker));
    _stateCheckers.push_back(StateChecker::SP(new SplitInconsistentStateChecker()));
    _stateCheckers.push_back(StateChecker::SP(new SynchronizeAndMoveStateChecker()));
    _stateCheckers.push_back(StateChecker::SP(new JoinBucketsStateChecker()));
    _stateCheckers.push_back(StateChecker::SP(new DeleteExtraCopiesStateChecker()));
    _stateCheckers.push_back(StateChecker::SP(new GarbageCollectionStateChecker()));
}

IdealStateManager::~IdealStateManager()
{
}

void
IdealStateManager::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "IdealStateManager";
}

bool
IdealStateManager::iAmUp() const
{
    Node node(NodeType::DISTRIBUTOR, _distributorComponent.getIndex());
    const lib::State &nodeState = _distributorComponent.getClusterState()
                    .getNodeState(node).getState();
    const lib::State &clusterState = _distributorComponent.getClusterState().getClusterState();

    return (nodeState == lib::State::UP && clusterState == lib::State::UP);
}

void
IdealStateManager::fillParentAndChildBuckets(StateChecker::Context& c) const
{
    _distributorComponent.getBucketDatabase().getAll(c.bucketId, c.entries);
    if (c.entries.empty()) {
        LOG(spam,
            "Did not find bucket %s in bucket database",
            c.bucketId.toString().c_str());
    }
}
void
IdealStateManager::fillSiblingBucket(StateChecker::Context& c) const
{
    c.siblingEntry = _distributorComponent.getBucketDatabase().get(c.siblingBucket);
}

BucketDatabase::Entry*
IdealStateManager::getEntryForPrimaryBucket(StateChecker::Context& c) const
{
    for (uint32_t j = 0; j < c.entries.size(); ++j) {
        BucketDatabase::Entry& e = c.entries[j];
        if (e.getBucketId() == c.bucketId) {
            return &e;
        }
    }
    return 0;
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
    for (uint32_t i = 0; i < _stateCheckers.size(); i++) {
        if (!_distributorComponent.getDistributor().getConfig().stateCheckerIsActive(
                _stateCheckers[i]->getName()))
        {
            LOG(spam, "Skipping state checker %s",
                _stateCheckers[i]->getName());
            continue;
        }

        auto result = _stateCheckers[i]->check(c);
        if (canOverwriteResult(highestPri, result)) {
            highestPri = std::move(result);
        }
    }
    return highestPri;
}

StateChecker::Result
IdealStateManager::generateHighestPriority(
        const document::BucketId& bid,
        NodeMaintenanceStatsTracker& statsTracker) const
{
    StateChecker::Context c(_distributorComponent, statsTracker, bid);
    fillParentAndChildBuckets(c);
    fillSiblingBucket(c);

    BucketDatabase::Entry* e(getEntryForPrimaryBucket(c));
    if (!e) {
        return StateChecker::Result::noMaintenanceNeeded();
    }
    LOG(spam, "Checking bucket %s", e->toString().c_str());

    c.entry = *e;
    return runStateCheckers(c);
}

MaintenancePriorityAndType
IdealStateManager::prioritize(
        const document::BucketId& bucketId,
        NodeMaintenanceStatsTracker& statsTracker) const
{
    StateChecker::Result generated(
            generateHighestPriority(bucketId, statsTracker));
    MaintenancePriority priority(generated.getPriority());
    MaintenanceOperation::Type type(priority.requiresMaintenance()
                                    ? generated.getType()
                                    : MaintenanceOperation::OPERATION_COUNT);
    return MaintenancePriorityAndType(priority, type);
}

IdealStateOperation::SP
IdealStateManager::generateInterceptingSplit(const BucketDatabase::Entry& e,
                                             api::StorageMessage::Priority pri)
{
    NodeMaintenanceStatsTracker statsTracker;
    StateChecker::Context c(_distributorComponent, statsTracker, e.getBucketId());
    if (e.valid()) {
        c.entry = e;

        IdealStateOperation::UP operation(
                _splitBucketStateChecker->check(c).createOperation());
        if (operation.get()) {
            operation->setPriority(pri);
            operation->setIdealStateManager(this);
        }

        return IdealStateOperation::SP(operation.release());
    }

    return IdealStateOperation::SP();
}

MaintenanceOperation::SP
IdealStateManager::generate(const document::BucketId& bucketId) const
{
    NodeMaintenanceStatsTracker statsTracker;
    IdealStateOperation::SP op(
            generateHighestPriority(bucketId, statsTracker).createOperation());
    if (op.get()) {
        op->setIdealStateManager(
                const_cast<IdealStateManager*>(this));
    }
    return op;
}

std::vector<MaintenanceOperation::SP>
IdealStateManager::generateAll(const document::BucketId& bucketId,
                               NodeMaintenanceStatsTracker& statsTracker) const
{
    StateChecker::Context c(_distributorComponent, statsTracker, bucketId);
    fillParentAndChildBuckets(c);
    fillSiblingBucket(c);
    BucketDatabase::Entry* e(getEntryForPrimaryBucket(c));
    std::vector<MaintenanceOperation::SP> operations;
    if (e) {
        c.entry = *e;
    } else {
        return operations;
    }

    for (uint32_t i = 0; i < _stateCheckers.size(); i++) {
        IdealStateOperation::UP op(
                _stateCheckers[i]->check(c).createOperation());
        if (op.get()) {
            operations.push_back(IdealStateOperation::SP(op.release()));
        }
    }
    return operations;
}

void
IdealStateManager::getBucketStatus(
        const BucketDatabase::Entry& entry,
        NodeMaintenanceStatsTracker& statsTracker,
        std::ostream& out) const
{
    LOG(debug, "Dumping bucket database valid at cluster state version %u",
        _distributorComponent.getDistributor().getClusterState().getVersion());

    std::vector<MaintenanceOperation::SP> operations(
            generateAll(entry.getBucketId(), statsTracker));
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

void
IdealStateManager::getBucketStatus(std::ostream& out) const
{
     StatusBucketVisitor proc(*this, out);
     _distributorComponent.getBucketDatabase().forEach(proc);
}

} // distributor
} // storage
