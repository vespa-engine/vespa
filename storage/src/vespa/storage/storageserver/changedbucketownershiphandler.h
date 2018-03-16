// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/metrics/metrics.h>
#include <vespa/config/config.h>
#include <vespa/config-persistence.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/persistence/messages.h>
#include <atomic>
#include <vector>
#include <unordered_map>

namespace storage {

/**
 * The changed bucket ownership handler is a storage link that synchronously
 * intercepts attempts to change the state on the node and ensure any
 * operations to buckets whose ownership is changed are aborted.
 *
 * If default config is used, all mutating ideal state operations for buckets
 * that--upon time of checking in this handler--belong to a different
 * distributor than the one specified as the sender will be aborted.
 *
 * We consider the following operations as mutating ideal state ops:
 *  - SplitBucketCommand
 *  - JoinBucketsCommand
 *  - MergeBucketsCommand (already blocked by throttler, but let's not
 *    let that stop us)
 *  - RemoveLocationCommand (technically an external load op, but is used by
 *    the GC functionality and must therefore be included here)
 *  - SetBucketStateCommand
 *  - DeleteBucketCommand
 *  - CreateBucketCommand
 *
 *  If default config is used, all mutating external operations with altered
 *  bucket owneship will also be aborted.
 *
 *  We consider the following external operations as mutating:
 *   - PutCommand
 *   - UpdateCommand
 *   - RemoveCommand
 *   - RevertCommand
 */
class ChangedBucketOwnershipHandler
    : public StorageLink,
      private config::IFetcherCallback<vespa::config::content::PersistenceConfig>
{
public:
    class Metrics : public metrics::MetricSet
    {
    public:
        metrics::LongAverageMetric averageAbortProcessingTime;
        metrics::LongCountMetric idealStateOpsAborted;
        metrics::LongCountMetric externalLoadOpsAborted;

        Metrics(metrics::MetricSet* owner = 0);
        ~Metrics();
    };

    /**
     * Wrapper around the distribution & state pairs that decides how to
     * compute the owner distributor for a bucket. It's possible to have
     * an ownership state with a nullptr cluster state when the node
     * initially starts up, which is why no owership state must be used unless
     * invoking valid() on it returns true.
     */
    class OwnershipState
    {
        using BucketSpace = document::BucketSpace;
        std::unordered_map<BucketSpace, std::shared_ptr<const lib::Distribution>, BucketSpace::hash> _distributions;
        lib::ClusterState::CSP _state;
    public:
        using SP = std::shared_ptr<OwnershipState>;
        using CSP = std::shared_ptr<const OwnershipState>;

        OwnershipState(const ContentBucketSpaceRepo &contentBucketSpaceRepo,
                       const lib::ClusterState::CSP& state);
        ~OwnershipState();

        static const uint16_t FAILED_TO_RESOLVE = 0xffff;

        bool valid() const {
            return (!_distributions.empty() && _state);
        }

        /**
         * Precondition: valid() == true.
         */
        const lib::ClusterState& getState() const {
            assert(valid());
            return *_state;
        }

        uint16_t ownerOf(const document::Bucket& bucket) const;
    };

    /**
     * For unit testing only; trigger a reload of the cluster state from the
     * component registry, since tests may want to set the cluster state
     * explicitly without sending a message through the chain.
     */
    void reloadClusterState();

private:
    ServiceLayerComponent _component;
    Metrics _metrics;
    config::ConfigFetcher _configFetcher;
    vespalib::Lock _stateLock;
    lib::ClusterState::CSP _currentState;
    OwnershipState::CSP _currentOwnership;

    std::atomic<bool> _abortQueuedAndPendingOnStateChange;
    std::atomic<bool> _abortMutatingIdealStateOps;
    std::atomic<bool> _abortMutatingExternalLoadOps;

    std::unique_ptr<AbortBucketOperationsCommand::AbortPredicate>
    makeLazyAbortPredicate(
            const OwnershipState::CSP& oldOwnership,
            const OwnershipState::CSP& newOwnership) const;

    void logTransition(const lib::ClusterState& currentState,
                       const lib::ClusterState& newState) const;

    /**
     * Creates a new immutable OwnershipState based on the current distribution
     * and the provided cluster state and assigns it to _currentOwnership.
     */
    void setCurrentOwnershipWithStateNoLock(const lib::ClusterState&);

    /**
     * Grabs _stateLock and returns a shared_ptr to the current ownership
     * state, which may or may not be valid().
     */
    OwnershipState::CSP getCurrentOwnershipState() const;

    bool isMutatingCommandAndNeedsChecking(const api::StorageMessage&) const;

    bool isMutatingIdealStateOperation(const api::StorageMessage&) const;

    bool isMutatingExternalOperation(const api::StorageMessage&) const;
    /**
     * Returns whether the operation in cmd has a bucket whose ownership in
     * the current cluster state does not match the distributor marked as
     * being the sender in the message itself.
     *
     * Precondition: cmd is an instance of a message type containing a bucket
     *     identifier.
     */
    bool sendingDistributorOwnsBucketInCurrentState(
            const api::StorageCommand& cmd) const;
    /**
     * Creates a reply for cmd, assigns an ABORTED return code and sends the
     * reply back up the storage chain.
     */
    void abortOperation(api::StorageCommand& cmd);

    /**
     * Returns whether aborting queued, changed ops and waiting for pending
     * changed ops is enabled through config.
     */
    bool enabledOperationAbortingOnStateChange() const;

    /**
     * Returns whether aborting outdated ideal state operations has been enabled
     * through config.
     */
    bool enabledIdealStateAborting() const;

    bool enabledExternalLoadAborting() const;

public:
    ChangedBucketOwnershipHandler(const config::ConfigUri& configUri,
                                  ServiceLayerComponentRegister& compReg);
    ~ChangedBucketOwnershipHandler();

    bool onSetSystemState(
            const std::shared_ptr<api::SetSystemStateCommand>&) override;
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;

    bool onInternalReply(
            const std::shared_ptr<api::InternalReply>& reply) override;

    void configure(std::unique_ptr<vespa::config::content::PersistenceConfig>) override;

    /**
     * We want to ensure distribution config changes are thread safe wrt. our
     * own state, so we make sure to get notified when these happen so we can
     * do explicit locked updates.
     */
    void storageDistributionChanged() override;

    const Metrics& getMetrics() const { return _metrics; }
};

}
