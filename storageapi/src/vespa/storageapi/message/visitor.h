// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file storageapi/message/visitor.h
 *
 * Messages related to visitors, used by the visitor manager.
 */

#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vdslib/container/parameters.h>
#include <vespa/vdslib/container/visitorstatistics.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/document/select/orderingspecification.h>

namespace storage::api {

/**
 * @class CreateVisitorCommand
 * @ingroup message
 *
 * @brief Command for creating a visitor.
 */
class CreateVisitorCommand : public StorageCommand {
private:
    document::BucketSpace _bucketSpace;
    vespalib::string _libName; // Name of visitor library to use, ie. DumpVisitor.so
    vdslib::Parameters _params;

    vespalib::string _controlDestination;
    vespalib::string _dataDestination;

    vespalib::string _docSelection;
    std::vector<document::BucketId> _buckets;
    Timestamp _fromTime;
    Timestamp _toTime;

    uint32_t _visitorCmdId;
    vespalib::string _instanceId;
    VisitorId _visitorId; // Set on storage node

    bool _visitRemoves;
    vespalib::string _fieldSet;
    bool _visitInconsistentBuckets;

    uint32_t _queueTimeout;
    uint32_t _maxPendingReplyCount;
    uint32_t _version;

    document::OrderingSpecification::Order _ordering;
    uint32_t _maxBucketsPerVisitor;

public:
    CreateVisitorCommand(document::BucketSpace bucketSpace,
                         const vespalib::stringref & libraryName,
                         const vespalib::stringref & instanceId,
                         const vespalib::stringref & docSelection);

    /** Create another command with similar visitor settings. */
    CreateVisitorCommand(const CreateVisitorCommand& template_);
    ~CreateVisitorCommand();

    void setVisitorCmdId(uint32_t id) { _visitorCmdId = id; }
    void setControlDestination(const vespalib::stringref & d)
        { _controlDestination = d; }
    void setDataDestination(const vespalib::stringref & d) { _dataDestination = d; }
    void setParameters(const vdslib::Parameters& params) { _params = params; }
    void setMaximumPendingReplyCount(uint32_t count)
    { _maxPendingReplyCount = count; }
    void setFieldSet(const vespalib::stringref& fieldSet)
    { _fieldSet = fieldSet; }
    void setVisitRemoves(bool value = true) { _visitRemoves = value; }
    void setVisitInconsistentBuckets(bool visitInconsistent = true)
        { _visitInconsistentBuckets = visitInconsistent; }
    void addBucketToBeVisited(const document::BucketId& id)
        { _buckets.push_back(id); }
    void setVisitorId(const VisitorId id) { _visitorId = id; }
    void setInstanceId(const vespalib::stringref & id) { _instanceId = id; }
    void setQueueTimeout(uint32_t milliSecs) { _queueTimeout = milliSecs; }
    void setFromTime(Timestamp ts) { _fromTime = ts; }
    void setToTime(Timestamp ts) { _toTime = ts; }

    VisitorId getVisitorId() const { return _visitorId; }
    uint32_t getVisitorCmdId() const { return _visitorCmdId; }
    document::BucketSpace getBucketSpace() const { return _bucketSpace; }
    document::Bucket getBucket() const override;
    const vespalib::string & getLibraryName() const { return _libName; }
    const vespalib::string & getInstanceId() const { return _instanceId; }
    const vespalib::string & getControlDestination() const
        { return _controlDestination; }
    const vespalib::string & getDataDestination() const { return _dataDestination; }
    const vespalib::string & getDocumentSelection() const { return _docSelection; }
    const vdslib::Parameters& getParameters() const { return _params; }
    vdslib::Parameters& getParameters() { return _params; }
    uint32_t getMaximumPendingReplyCount() const
        { return _maxPendingReplyCount; }
    const std::vector<document::BucketId>& getBuckets() const
        { return _buckets; }
    Timestamp getFromTime() const { return _fromTime; }
    Timestamp getToTime() const { return _toTime; }
    std::vector<document::BucketId>& getBuckets() { return _buckets; }
    bool visitRemoves() const { return _visitRemoves; }
    const vespalib::string& getFieldSet() const { return _fieldSet; }
    bool visitInconsistentBuckets() const { return _visitInconsistentBuckets; }
        // In millisec
    uint32_t getQueueTimeout() const { return _queueTimeout; }

    void setVisitorDispatcherVersion(uint32_t version) { _version = version; }
    uint32_t getVisitorDispatcherVersion() const { return _version; }

    void setVisitorOrdering(document::OrderingSpecification::Order ordering) { _ordering = ordering; }
    document::OrderingSpecification::Order getVisitorOrdering() const { return _ordering; }
    void setMaxBucketsPerVisitor(uint32_t max) { _maxBucketsPerVisitor = max; }
    uint32_t getMaxBucketsPerVisitor() const { return _maxBucketsPerVisitor; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(CreateVisitorCommand, onCreateVisitor)
};

/**
 * @class CreateVisitorReply
 * @ingroup message
 *
 * @brief Response to a create visitor command.
 */
class CreateVisitorReply : public StorageReply {
private:
    document::BucketId _lastBucket;
    vdslib::VisitorStatistics _visitorStatistics;

public:
    explicit CreateVisitorReply(const CreateVisitorCommand& cmd);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void setLastBucket(const document::BucketId& lastBucket) { _lastBucket = lastBucket; }

    const document::BucketId& getLastBucket() const { return _lastBucket; }

    void setVisitorStatistics(const vdslib::VisitorStatistics& stats) { _visitorStatistics = stats; }

    const vdslib::VisitorStatistics& getVisitorStatistics() const { return _visitorStatistics; }

    DECLARE_STORAGEREPLY(CreateVisitorReply, onCreateVisitorReply)
};

/**
 * @class DestroyVisitorCommand
 * @ingroup message
 *
 * @brief Command for removing a visitor.
 */
class DestroyVisitorCommand : public StorageCommand {
private:
    vespalib::string  _instanceId;

public:
    explicit DestroyVisitorCommand(const vespalib::stringref & instanceId);

    const vespalib::string & getInstanceId() const { return _instanceId; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(DestroyVisitorCommand, onDestroyVisitor)
};

/**
 * @class DestroyVisitorReply
 * @ingroup message
 *
 * @brief Response to a destroy visitor command.
 */
class DestroyVisitorReply : public StorageReply {
public:
    explicit DestroyVisitorReply(const DestroyVisitorCommand& cmd);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(DestroyVisitorReply, onDestroyVisitorReply)
};

/**
 * @class VisitorInfoCommand
 * @ingroup message
 *
 * @brief Sends status information of an ongoing visitor.
 *
 * Includes three different kinds of data.
 *  - Notification when visiting is complete.
 *  - Notification when individual buckets have been completely visited.
 *    (Including the timestamp of the newest document visited)
 *  - Notification that some error condition arose during visiting.
 */
class VisitorInfoCommand : public StorageCommand {
public:
    struct BucketTimestampPair {
        document::BucketId bucketId;
        Timestamp timestamp;

        BucketTimestampPair() : bucketId(), timestamp(0) {}
        BucketTimestampPair(const document::BucketId& bucket,
                            const Timestamp& ts)
            : bucketId(bucket), timestamp(ts) {}

        bool operator==(const BucketTimestampPair& other) const {
            return (bucketId == other.bucketId && timestamp && other.timestamp);
        }
    };

private:
    bool _completed;
    std::vector<BucketTimestampPair> _bucketsCompleted;
    ReturnCode _error;

public:
    VisitorInfoCommand();
    ~VisitorInfoCommand();

    void setErrorCode(const ReturnCode& code) { _error = code; }
    void setCompleted() { _completed = true; }
    void setBucketCompleted(const document::BucketId& id, Timestamp lastVisited)
    {
        _bucketsCompleted.push_back(BucketTimestampPair(id, lastVisited));
    }
    void setBucketsCompleted(const std::vector<BucketTimestampPair>& bc)
        { _bucketsCompleted = bc; }

    const ReturnCode& getErrorCode() const { return _error; }
    const std::vector<BucketTimestampPair>& getCompletedBucketsList() const
        { return _bucketsCompleted; }
    bool visitorCompleted() const { return _completed; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(VisitorInfoCommand, onVisitorInfo)
};

std::ostream& operator<<(std::ostream& out, const VisitorInfoCommand::BucketTimestampPair& pair);

class VisitorInfoReply : public StorageReply {
    bool _completed;

public:
    VisitorInfoReply(const VisitorInfoCommand& cmd);
    bool visitorCompleted() const { return _completed; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(VisitorInfoReply, onVisitorInfoReply)
};

}
