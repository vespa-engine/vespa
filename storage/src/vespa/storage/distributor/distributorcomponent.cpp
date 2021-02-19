// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributorcomponent.h"
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include "pendingmessagetracker.h"
#include <vespa/document/select/parser.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributorstoragelink");

using document::BucketSpace;

namespace storage::distributor {

DistributorComponent::DistributorComponent(
        DistributorInterface& distributor,
        DistributorBucketSpaceRepo& bucketSpaceRepo,
        DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
        DistributorComponentRegister& compReg,
        const std::string& name)
    : storage::DistributorComponent(compReg, name),
      _distributor(distributor),
      _bucketSpaceRepo(bucketSpaceRepo),
      _readOnlyBucketSpaceRepo(readOnlyBucketSpaceRepo)
{
}

DistributorComponent::~DistributorComponent() = default;

void
DistributorComponent::sendDown(const api::StorageMessage::SP& msg)
{
    _distributor.getMessageSender().sendDown(msg);
}

void
DistributorComponent::sendUp(const api::StorageMessage::SP& msg)
{
    _distributor.getMessageSender().sendUp(msg);
}

const lib::ClusterStateBundle&
DistributorComponent::getClusterStateBundle() const
{
    return _distributor.getClusterStateBundle();
};

api::StorageMessageAddress
DistributorComponent::nodeAddress(uint16_t nodeIndex) const
{
    return api::StorageMessageAddress::create(cluster_name_ptr(), lib::NodeType::STORAGE, nodeIndex);
}

bool
DistributorComponent::checkDistribution(api::StorageCommand &cmd, const document::Bucket &bucket)
{
    auto &bucket_space(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    BucketOwnership bo(bucket_space.check_ownership_in_pending_and_current_state(bucket.getBucketId()));
    if (!bo.isOwned()) {
        std::string systemStateStr = bo.getNonOwnedState().toString();
        LOG(debug,
            "Got message with wrong distribution, bucket %s sending back state '%s'",
            bucket.toString().c_str(), systemStateStr.c_str());

        api::StorageReply::UP reply(cmd.makeReply());
        api::ReturnCode ret(api::ReturnCode::WRONG_DISTRIBUTION, systemStateStr);
        reply->setResult(ret);
        sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
        return false;
    }
    return true;
}

void
DistributorComponent::removeNodesFromDB(const document::Bucket &bucket, const std::vector<uint16_t>& nodes)
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    BucketDatabase::Entry dbentry = bucketSpace.getBucketDatabase().get(bucket.getBucketId());

    if (dbentry.valid()) {
        for (uint32_t i = 0; i < nodes.size(); ++i) {
            if (dbentry->removeNode(nodes[i])) {
                LOG(debug,
                "Removed node %d from bucket %s. %u copies remaining",
                    nodes[i],
                    bucket.toString().c_str(),
                    dbentry->getNodeCount());
            }
        }

        if (dbentry->getNodeCount() != 0) {
            bucketSpace.getBucketDatabase().update(dbentry);
        } else {
            LOG(debug,
                "After update, bucket %s now has no copies. "
                "Removing from database.",
                bucket.toString().c_str());

            bucketSpace.getBucketDatabase().remove(bucket.getBucketId());
        }
    }
}

void
DistributorComponent::enumerateUnavailableNodes(
        std::vector<uint16_t>& unavailableNodes,
        const lib::ClusterState& s,
        const document::Bucket& bucket,
        const std::vector<BucketCopy>& candidates) const
{
    const auto* up_states = _distributor.getStorageNodeUpStates();
    for (uint32_t i = 0; i < candidates.size(); ++i) {
        const BucketCopy& copy(candidates[i]);
        const lib::NodeState& ns(
                s.getNodeState(lib::Node(lib::NodeType::STORAGE, copy.getNode())));
        if (!ns.getState().oneOf(up_states)) {
            LOG(debug,
                "Trying to add a bucket copy to %s whose node is marked as "
                "down in the cluster state: %s. Ignoring it since no zombies "
                "are allowed!",
                bucket.toString().c_str(),
                copy.toString().c_str());
            unavailableNodes.emplace_back(copy.getNode());
        }
    }
}

namespace {

/**
 * Helper class to update entry in bucket database when bucket copies from nodes have changed.
 */
class UpdateBucketDatabaseProcessor : public BucketDatabase::EntryUpdateProcessor {
    const framework::Clock& _clock;
    const std::vector<BucketCopy>& _changed_nodes;
    std::vector<uint16_t> _ideal_nodes;
    bool _reset_trusted;
public:
    UpdateBucketDatabaseProcessor(const framework::Clock& clock, const std::vector<BucketCopy>& changed_nodes, std::vector<uint16_t> ideal_nodes, bool reset_trusted);
    virtual ~UpdateBucketDatabaseProcessor();
    virtual BucketDatabase::Entry create_entry(const document::BucketId& bucket) const override;
    virtual bool process_entry(BucketDatabase::Entry &entry) const override;
};

UpdateBucketDatabaseProcessor::UpdateBucketDatabaseProcessor(const framework::Clock& clock, const std::vector<BucketCopy>& changed_nodes, std::vector<uint16_t> ideal_nodes, bool reset_trusted)
    : BucketDatabase::EntryUpdateProcessor(),
      _clock(clock),
      _changed_nodes(changed_nodes),
      _ideal_nodes(std::move(ideal_nodes)),
      _reset_trusted(reset_trusted)
{
}

UpdateBucketDatabaseProcessor::~UpdateBucketDatabaseProcessor() = default;

BucketDatabase::Entry
UpdateBucketDatabaseProcessor::create_entry(const document::BucketId &bucket) const
{
    return BucketDatabase::Entry(bucket, BucketInfo());
}

bool
UpdateBucketDatabaseProcessor::process_entry(BucketDatabase::Entry &entry) const
{
    // 0 implies bucket was just added. Since we don't know if any other
    // distributor has run GC on it, we just have to assume this and set the
    // timestamp to the current time to avoid duplicate work.
    if (entry->getLastGarbageCollectionTime() == 0) {
        entry->setLastGarbageCollectionTime(_clock.getTimeInSeconds().getTime());
    }
    entry->addNodes(_changed_nodes, _ideal_nodes);
    if (_reset_trusted) {
        entry->resetTrusted();
    }
    if (entry->getNodeCount() == 0) {
        LOG(warning, "all nodes in changedNodes set (size %zu) are down, removing dbentry", _changed_nodes.size());
        return false; // remove entry
    }
    return true; // keep entry
}

}

void
DistributorComponent::updateBucketDatabase(
        const document::Bucket &bucket,
        const std::vector<BucketCopy>& changedNodes,
        uint32_t updateFlags)
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    assert(!(bucket.getBucketId() == document::BucketId()));

    BucketOwnership ownership(bucketSpace.check_ownership_in_pending_and_current_state(bucket.getBucketId()));
    if (!ownership.isOwned()) {
        LOG(debug,
            "Trying to add %s to database that we do not own according to "
            "cluster state '%s' - ignoring!",
            bucket.toString().c_str(),
            ownership.getNonOwnedState().toString().c_str());
        return;
    }

    // Ensure that we're not trying to bring any zombie copies into the
    // bucket database (i.e. copies on nodes that are actually unavailable).
    const auto& available_nodes = bucketSpace.get_available_nodes();
    bool found_down_node = false;
    for (const auto& copy : changedNodes) {
        if (copy.getNode() >= available_nodes.size() || !available_nodes[copy.getNode()]) {
            found_down_node = true;
            break;
        }
    }
    // Optimize for common case where we don't have to create a new
    // bucket copy vector
    std::vector<BucketCopy> up_nodes;
    if (found_down_node) {
        up_nodes.reserve(changedNodes.size());
        for (uint32_t i = 0; i < changedNodes.size(); ++i) {
            const BucketCopy& copy(changedNodes[i]);
            if (copy.getNode() < available_nodes.size() && available_nodes[copy.getNode()]) {
                up_nodes.emplace_back(copy);
            }
        }
    }

    UpdateBucketDatabaseProcessor processor(getClock(), found_down_node ? up_nodes : changedNodes, bucketSpace.get_ideal_service_layer_nodes_bundle(bucket.getBucketId()).get_available_nodes(), (updateFlags & DatabaseUpdate::RESET_TRUSTED) != 0);

    bucketSpace.getBucketDatabase().process_update(bucket.getBucketId(), processor, (updateFlags & DatabaseUpdate::CREATE_IF_NONEXISTING) != 0);
}

void
DistributorComponent::recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket)
{
    _distributor.recheckBucketInfo(nodeIdx, bucket);
}

document::BucketId
DistributorComponent::getBucketId(const document::DocumentId& docId) const
{
    document::BucketId id(getBucketIdFactory().getBucketId(docId));

    id.setUsedBits(_distributor.getConfig().getMinimalBucketSplit());
    return id.stripUnused();
}

bool
DistributorComponent::storageNodeIsUp(document::BucketSpace bucketSpace, uint32_t nodeIndex) const
{
    const lib::NodeState& ns = getClusterStateBundle().getDerivedClusterState(bucketSpace)->getNodeState(
            lib::Node(lib::NodeType::STORAGE, nodeIndex));

    return ns.getState().oneOf(_distributor.getStorageNodeUpStates());
}

document::BucketId
DistributorComponent::getSibling(const document::BucketId& bid) const {
    document::BucketId zeroBucket;
    document::BucketId oneBucket;

    if (bid.getUsedBits() == 1) {
        zeroBucket = document::BucketId(1, 0);
        oneBucket = document::BucketId(1, 1);
    } else {
        document::BucketId joinedBucket = document::BucketId(
                bid.getUsedBits() - 1,
                bid.getId());

        zeroBucket = document::BucketId(
                bid.getUsedBits(),
                joinedBucket.getId());

        uint64_t hiBit = 1;
        hiBit <<= (bid.getUsedBits() - 1);
        oneBucket = document::BucketId(
                bid.getUsedBits(),
                joinedBucket.getId() | hiBit);
    }

    return (zeroBucket == bid) ? oneBucket : zeroBucket;
};

BucketDatabase::Entry
DistributorComponent::createAppropriateBucket(const document::Bucket &bucket)
{
    auto &bucketSpace(_bucketSpaceRepo.get(bucket.getBucketSpace()));
    return bucketSpace.getBucketDatabase().createAppropriateBucket(
            _distributor.getConfig().getMinimalBucketSplit(),
            bucket.getBucketId());
}

bool
DistributorComponent::initializing() const {
    return _distributor.initializing();
}

bool
DistributorComponent::has_pending_message(uint16_t node_index,
                                          const document::Bucket& bucket,
                                          uint32_t message_type) const
{
    const auto& sender = static_cast<const DistributorMessageSender&>(getDistributor());
    return sender.getPendingMessageTracker().hasPendingMessage(node_index, bucket, message_type);
}

std::unique_ptr<document::select::Node>
DistributorComponent::parse_selection(const vespalib::string& selection) const
{
    document::select::Parser parser(*getTypeRepo()->documentTypeRepo, getBucketIdFactory());
    return parser.parse(selection);
}

}
