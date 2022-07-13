// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/maintenance/maintenanceoperation.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storageapi/messageapi/maintenancecommand.h>
#include <vespa/document/bucket/bucketid.h>

namespace storage::distributor {

class DistributorBucketSpace;
class PendingMessageTracker;
class IdealStateManager;

/**
   @class BucketAndNodes

   Represents a target for an ideal state operation, consisting of a set of storage nodes
   and a bucket id.

   BucketAndNodes has a default sort order of nodes first (having already sorted the nodes
   in numerical order), then BucketId, so that it can be used for scheduling by the
   @link StateChecker class.
*/
class BucketAndNodes
{
public:
    /**
       Constructor for operations having only one node.

       @param bucket Target bucket
       @param node Target node
    */
    BucketAndNodes(const document::Bucket &bucket, uint16_t node);

    /**
       Constructor for operations with multiple target nodes.

       @param bucket Target bucket
       @param nodes Target nodes
    */
    BucketAndNodes(const document::Bucket &bucket,
                   const std::vector<uint16_t>& nodes);

    /**
       Changes the target bucket.

       @param id The new target bucket
    */
    void setBucketId(const document::BucketId &id);

    /**
       Returns the target bucket.

       @return Returns the target bucket.
    */
    document::BucketId getBucketId() const { return _bucket.getBucketId(); }

    document::Bucket getBucket() const { return _bucket; }

    /**
       Returns the target nodes

       @return the target nodes
    */
    std::vector<uint16_t>& getNodes() { return _nodes; }

    /**
       Returns the target nodes

       @return the target nodes
    */
    const std::vector<uint16_t>& getNodes() const { return _nodes; }

    /**
       Returns a string representation of this object.

       @return String representation
    */
    std::string toString() const;

private:
    document::Bucket      _bucket;
    std::vector<uint16_t> _nodes;
};

/**
   @class Operation

   Superclass for ideal state operations started by the IdealStateManager.
   Each operation has a target (BucketAndNodes), and a pointer back to the
   IdealStateManager.

   An operation is started by the start() method (from @link Callback), and
   may send messages there. Once replies are received, the receive() method
   (also from @link Callback) is called. When the operation is done, it should
   call done(), where this class will call back to the IdealStateManager
   with operationFinished(), so that the IdealStateManager can update its
   active state, possibly reschedule other operations in the same OperationList
   as this one, or recheck the bucket.
*/
class IdealStateOperation : public MaintenanceOperation
{
public:
    static const uint32_t MAINTENANCE_MESSAGE_TYPES[];

    typedef std::shared_ptr<IdealStateOperation> SP;
    typedef std::unique_ptr<IdealStateOperation> UP;
    typedef std::vector<SP> Vector;
    typedef std::map<document::BucketId, SP> Map;

    IdealStateOperation(const BucketAndNodes& bucketAndNodes);

    virtual ~IdealStateOperation();

    void onClose(DistributorStripeMessageSender&) override {}

    /**
       Returns true if the operation was performed successfully.

       @return Returns the status of the operation.
    */
    virtual bool ok() { return _ok; }

    /**
       Returns the target nodes of the operation.

       @return The target nodes
    */
    std::vector<uint16_t>& getNodes() { return _bucketAndNodes.getNodes(); }

    /**
       Returns the target nodes of the operation.

       @return The target nodes
    */
    const std::vector<uint16_t>& getNodes() const { return _bucketAndNodes.getNodes(); }

    /**
       Returns the target bucket of the operation.

       @return The target bucket.
    */
    document::BucketId getBucketId() const { return _bucketAndNodes.getBucketId(); }

    document::Bucket getBucket() const { return _bucketAndNodes.getBucket(); }

    /**
       Returns the target of the operation.

       @return The target bucket and nodes
    */
    const BucketAndNodes& getBucketAndNodes() const { return _bucketAndNodes; }

    /**
       Called by the operation when it is finished. Must be called, otherwise the active
       state won't be updated correctly.
    */
    virtual void done();

    void on_blocked() override;

    void on_throttled() override;

    /**
       Called by IdealStateManager to allow the operation to call back its
       OperationFinished() method when done.

       @param manager The ideal state manager.
    */
    void setIdealStateManager(IdealStateManager* manager);

    /**
       Returns the type of operation this is.
    */
    virtual Type getType() const = 0;

    /**
       Set the priority we should send messages from this operation with.
    */
    void setPriority(api::StorageMessage::Priority priority) noexcept {
        _priority = priority;
    }

    /**
     * Returns true if we are blocked to start this operation given
     * the pending messages.
     */
    bool isBlocked(const DistributorStripeOperationContext& ctx, const OperationSequencer&) const override;

    /**
       Returns the priority we should send messages with.
    */
    api::StorageMessage::Priority getPriority() { return _priority; }

    void setDetailedReason(const std::string& detailedReason) {
        _detailedReason = detailedReason;
    }
    void setDetailedReason(std::string&& detailedReason) {
        _detailedReason = std::move(detailedReason);
    }

    const std::string& getDetailedReason() const override {
        return _detailedReason;
    }

    uint32_t memorySize() const;

    /**
     * Sets the various metadata for the given command that
     * is common for all ideal state operations.
     */
    void setCommandMeta(api::MaintenanceCommand& cmd) const;

    std::string toString() const override;

    /**
     * Should return true if the given message type should block this operation.
     */
    virtual bool shouldBlockThisOperation(uint32_t messageType, uint16_t node, uint8_t priority) const;

protected:
    friend struct IdealStateManagerTest;
    friend class IdealStateManager;

    IdealStateManager* _manager;
    DistributorBucketSpace *_bucketSpace;
    BucketAndNodes _bucketAndNodes;
    std::string _detailedReason;

    bool _ok;
    api::StorageMessage::Priority _priority;

    /**
     * Checks if the given bucket is blocked by any pending messages to any
     * node _explicitly part of this ideal state operation_. If there are
     * operations to other nodes for this bucket, these will not be part of
     * the set of messages checked.
     */
    bool checkBlock(const document::Bucket& bucket,
                    const DistributorStripeOperationContext& ctx,
                    const OperationSequencer&) const;
    bool checkBlockForAllNodes(const document::Bucket& bucket,
                               const DistributorStripeOperationContext& ctx,
                               const OperationSequencer&) const;

};

}
