// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "pending_bucket_space_db_transition_entry.h"
#include "clusterinformation.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vespalib/util/xmlserializable.h>
#include "outdated_nodes_map.h"
#include <unordered_map>
#include <deque>

namespace storage::distributor {

class DistributorMessageSender;
class PendingBucketSpaceDbTransition;
class DistributorBucketSpaceRepo;

/**
 * Class used by BucketDBUpdater to track request bucket info
 * messages sent to the storage nodes.
 */
class PendingClusterState : public vespalib::XmlSerializable {
public:
    using OutdatedNodes = dbtransition::OutdatedNodes;
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;
    struct Summary {
        Summary(const std::string& prevClusterState, const std::string& newClusterState, uint32_t processingTime);
        Summary(const Summary &);
        Summary & operator = (const Summary &);
        Summary(Summary &&) = default;
        Summary & operator = (Summary &&) = default;
        ~Summary();

        std::string _prevClusterState;
        std::string _newClusterState;
        uint32_t _processingTime;
    };

    static std::unique_ptr<PendingClusterState> createForClusterStateChange(
            const framework::Clock& clock,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            DistributorBucketSpaceRepo &bucketSpaceRepo,
            const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
            const OutdatedNodesMap &outdatedNodesMap,
            api::Timestamp creationTimestamp)
    {
        return std::unique_ptr<PendingClusterState>(
                new PendingClusterState(clock, clusterInfo, sender, bucketSpaceRepo, newStateCmd,
                                        outdatedNodesMap,
                                        creationTimestamp));
    }

    /**
     * Distribution changes always need to ask all storage nodes, so no
     * need to do an union of existing outdated nodes; implicit complete set.
     */
    static std::unique_ptr<PendingClusterState> createForDistributionChange(
            const framework::Clock& clock,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            DistributorBucketSpaceRepo &bucketSpaceRepo,
            api::Timestamp creationTimestamp)
    {
        return std::unique_ptr<PendingClusterState>(
                new PendingClusterState(clock, clusterInfo, sender, bucketSpaceRepo, creationTimestamp));
    }

    PendingClusterState(const PendingClusterState &) = delete;
    PendingClusterState & operator = (const PendingClusterState &) = delete;
    ~PendingClusterState();

    /**
     * Adds the info from the reply to our list of information.
     * Returns true if the reply was accepted by this object, false if not.
     */
    bool onRequestBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply>& reply);

    /**
     * Tags the given node as having replied to at least one of the
     * request bucket info commands. Only used for debug logging.
     */
    void setNodeReplied(uint16_t nodeIdx) {
        _requestedNodes[nodeIdx] = true;
    }

    /** Called to resend delayed resends due to failures. */
    void resendDelayedMessages();

    /**
     * Returns true if all the nodes we requested have replied to
     * the request bucket info commands.
     */
    bool done() {
        return _sentMessages.empty() && _delayedRequests.empty();
    }

    bool hasBucketOwnershipTransfer() const noexcept {
        return _bucketOwnershipTransfer;
    }

    std::shared_ptr<api::SetSystemStateCommand> getCommand() {
        return _cmd;
    }

    const lib::ClusterStateBundle& getNewClusterStateBundle() const {
        return _newClusterStateBundle;
    }

    /**
     * Returns the union set of the outdated node set provided at construction
     * time and the set of nodes that the pending cluster state figured out
     * were outdated based on the cluster state diff. If the pending cluster
     * state was constructed for a distribution config change, this set will
     * be equal to the set of all available storage nodes.
     */
    OutdatedNodesMap getOutdatedNodesMap() const;

    /**
     * Merges all the results with the corresponding bucket databases.
     */
    void mergeIntoBucketDatabases();
    // Get pending transition for a specific bucket space. Only used by unit test.
    PendingBucketSpaceDbTransition &getPendingBucketSpaceDbTransition(document::BucketSpace bucketSpace);

    void printXml(vespalib::XmlOutputStream&) const override;
    Summary getSummary() const;
    std::string requestNodesToString() const;

private:
    /**
     * Creates a pending cluster state that represents
     * a set system state command from the fleet controller.
     */
    PendingClusterState(
            const framework::Clock&,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            DistributorBucketSpaceRepo &bucketSpaceRepo,
            const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
            const OutdatedNodesMap &outdatedNodesMap,
            api::Timestamp creationTimestamp);

    /**
     * Creates a pending cluster state that represents a distribution
     * change.
     */
    PendingClusterState(
            const framework::Clock&,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            DistributorBucketSpaceRepo &bucketSpaceRepo,
            api::Timestamp creationTimestamp);

    struct BucketSpaceAndNode {
        document::BucketSpace bucketSpace;
        uint16_t              node;
        BucketSpaceAndNode(document::BucketSpace bucketSpace_,
                           uint16_t node_)
            : bucketSpace(bucketSpace_),
              node(node_)
        {
        }
    };

    void initializeBucketSpaceTransitions(bool distributionChanged, const OutdatedNodesMap &outdatedNodesMap);
    void logConstructionInformation() const;
    void requestNode(BucketSpaceAndNode bucketSpaceAndNode);
    void requestNodes();
    void requestBucketInfoFromStorageNodesWithChangedState();

    /**
     * Number of nodes with node type 'storage' in _newClusterState.
     */
    uint16_t newStateStorageNodeCount() const;
    bool shouldRequestBucketInfo() const;
    bool clusterIsDown() const;
    bool iAmDown() const;

    bool storageNodeUpInNewState(document::BucketSpace bucketSpace, uint16_t node) const;
    std::string getNewClusterStateBundleString() const {
        return _newClusterStateBundle.getBaselineClusterState()->toString();
    }
    std::string getPrevClusterStateBundleString() const {
        return _prevClusterStateBundle.getBaselineClusterState()->toString();
    }

    std::shared_ptr<api::SetSystemStateCommand> _cmd;

    std::map<uint64_t, BucketSpaceAndNode> _sentMessages;
    std::vector<bool> _requestedNodes;
    std::deque<std::pair<framework::MilliSecTime, BucketSpaceAndNode> > _delayedRequests;

    lib::ClusterStateBundle _prevClusterStateBundle;
    lib::ClusterStateBundle _newClusterStateBundle;

    const framework::Clock& _clock;
    ClusterInformation::CSP _clusterInfo;
    api::Timestamp _creationTimestamp;

    DistributorMessageSender& _sender;
    DistributorBucketSpaceRepo &_bucketSpaceRepo;

    bool _bucketOwnershipTransfer;
    std::unordered_map<document::BucketSpace, std::unique_ptr<PendingBucketSpaceDbTransition>, document::BucketSpace::hash> _pendingTransitions;
};

}
