// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "node_supported_features.h"
#include "pending_bucket_space_db_transition_entry.h"
#include "clusterinformation.h"
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vespalib/util/xmlserializable.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include "outdated_nodes_map.h"
#include <unordered_map>
#include <deque>

namespace storage::framework { struct Clock; }

namespace storage::distributor {

class BucketSpaceStateMap;
class DistributorMessageSender;
class PendingBucketSpaceDbTransition;
class StripeAccessGuard;

/**
 * Class used by TopLevelBucketDBUpdater to track request bucket info
 * messages sent to the storage nodes.
 */
class PendingClusterState : public vespalib::XmlSerializable {
public:
    using OutdatedNodes = dbtransition::OutdatedNodes;
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;
    struct Summary {
        Summary(std::string prevClusterState, std::string newClusterState, vespalib::duration processingTime);
        Summary(const Summary &);
        Summary & operator = (const Summary &);
        Summary(Summary &&) noexcept = default;
        Summary & operator = (Summary &&) noexcept = default;
        ~Summary();

        std::string _prevClusterState;
        std::string _newClusterState;
        vespalib::duration _processingTime;
    };

    static std::unique_ptr<PendingClusterState> createForClusterStateChange(
            const framework::Clock& clock,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            const BucketSpaceStateMap& bucket_space_states,
            const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
            const OutdatedNodesMap& outdatedNodesMap,
            api::Timestamp creationTimestamp)
    {
        // Naked new due to private constructor
        return std::unique_ptr<PendingClusterState>(new PendingClusterState(
                clock, clusterInfo, sender, bucket_space_states,
                newStateCmd, outdatedNodesMap, creationTimestamp));
    }

    /**
     * Distribution changes always need to ask all storage nodes, so no
     * need to do an union of existing outdated nodes; implicit complete set.
     */
    static std::unique_ptr<PendingClusterState> createForDistributionChange(
            const framework::Clock& clock,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            const BucketSpaceStateMap& bucket_space_states,
            api::Timestamp creationTimestamp)
    {
        // Naked new due to private constructor
        return std::unique_ptr<PendingClusterState>(new PendingClusterState(
                clock, clusterInfo, sender, bucket_space_states, creationTimestamp));
    }

    PendingClusterState(const PendingClusterState &) = delete;
    PendingClusterState & operator = (const PendingClusterState &) = delete;
    ~PendingClusterState() override;

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
    [[nodiscard]] bool done() const noexcept {
        return _sentMessages.empty() && _delayedRequests.empty();
    }

    bool hasBucketOwnershipTransfer() const noexcept {
        return _bucketOwnershipTransfer;
    }

    bool hasCommand() const noexcept {
        return (_cmd.get() != nullptr);
    }

    std::shared_ptr<api::SetSystemStateCommand> getCommand() const noexcept {
        return _cmd;
    }

    bool isVersionedTransition() const noexcept {
        return _isVersionedTransition;
    }

    uint32_t clusterStateVersion() const noexcept {
        return _clusterStateVersion;
    }

    bool isDeferred() const noexcept {
        return (isVersionedTransition()
                && _newClusterStateBundle.deferredActivation());
    }

    void clearCommand() noexcept {
        _cmd.reset();
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
    void merge_into_bucket_databases(StripeAccessGuard& guard);

    // Get pending transition for a specific bucket space. Only used by unit test.
    PendingBucketSpaceDbTransition& getPendingBucketSpaceDbTransition(document::BucketSpace bucketSpace);

    // May be a subset of the nodes in the cluster, depending on how many nodes were consulted
    // as part of the pending cluster state. Caller must take care to aggregate features.
    const vespalib::hash_map<uint16_t, NodeSupportedFeatures>& gathered_node_supported_features() const noexcept {
        return _node_features;
    }

    void printXml(vespalib::XmlOutputStream&) const override;
    Summary getSummary() const;

private:
    // With 100ms resend timeout, this requires a particular node to have failed
    // for _at least_ threshold/10 seconds before a log warning is emitted.
    constexpr static size_t RequestFailureWarningEdgeTriggerThreshold = 200;

    /**
     * Creates a pending cluster state that represents
     * a set system state command from the cluster controller.
     */
    PendingClusterState(
            const framework::Clock&,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            const BucketSpaceStateMap& bucket_space_states,
            const std::shared_ptr<api::SetSystemStateCommand>& newStateCmd,
            const OutdatedNodesMap& outdatedNodesMap,
            api::Timestamp creationTimestamp);

    /**
     * Creates a pending cluster state that represents a distribution
     * change.
     */
    PendingClusterState(
            const framework::Clock&,
            const ClusterInformation::CSP& clusterInfo,
            DistributorMessageSender& sender,
            const BucketSpaceStateMap& bucket_space_states,
            api::Timestamp creationTimestamp);

    struct BucketSpaceAndNode {
        document::BucketSpace bucketSpace;
        uint16_t              node;
        BucketSpaceAndNode(document::BucketSpace bucketSpace_, uint16_t node_)
            : bucketSpace(bucketSpace_),
              node(node_)
        {
        }
    };

    void initializeBucketSpaceTransitions(bool distributionChanged, const OutdatedNodesMap& outdatedNodesMap);
    void logConstructionInformation() const;
    void requestNode(BucketSpaceAndNode bucketSpaceAndNode);
    void requestNodes();
    void requestBucketInfoFromStorageNodesWithChangedState();

    bool shouldRequestBucketInfo() const;
    bool clusterIsDown() const;
    bool iAmDown() const;

    bool storageNodeUpInNewState(document::BucketSpace bucketSpace, uint16_t node) const;
    std::string getNewClusterStateBundleString() const;
    std::string getPrevClusterStateBundleString() const;
    void update_reply_failure_statistics(const api::ReturnCode& result, const BucketSpaceAndNode& source);
    void update_node_supported_features_from_reply(uint16_t node, const api::RequestBucketInfoReply& reply);

    using SentMessages       = std::map<uint64_t, BucketSpaceAndNode>;
    using DelayedRequests    = std::deque<std::pair<vespalib::steady_time , BucketSpaceAndNode>>;
    using PendingTransitions = std::unordered_map<document::BucketSpace, std::unique_ptr<PendingBucketSpaceDbTransition>, document::BucketSpace::hash>;
    using NodeFeatures       = vespalib::hash_map<uint16_t, NodeSupportedFeatures>;

    std::shared_ptr<api::SetSystemStateCommand> _cmd;

    SentMessages               _sentMessages;
    std::vector<bool>          _requestedNodes;
    DelayedRequests            _delayedRequests;

    lib::ClusterStateBundle    _prevClusterStateBundle;
    lib::ClusterStateBundle    _newClusterStateBundle;

    const framework::Clock&    _clock;
    ClusterInformation::CSP    _clusterInfo;
    api::Timestamp             _creationTimestamp;
    DistributorMessageSender&  _sender;
    const BucketSpaceStateMap& _bucket_space_states;
    uint32_t                   _clusterStateVersion;
    bool                       _isVersionedTransition;
    bool                       _bucketOwnershipTransfer;
    PendingTransitions         _pendingTransitions;
    NodeFeatures               _node_features;
};

}
