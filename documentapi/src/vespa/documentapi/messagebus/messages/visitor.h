// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "writedocumentreply.h"
#include "documentmessage.h"
#include "documentreply.h"
#include <vespa/vdslib/container/parameters.h>
#include <vespa/vdslib/container/visitorstatistics.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/select/orderingspecification.h>
#include <vespa/document/fieldvalue/document.h>


namespace documentapi {

typedef uint64_t Timestamp;

/**
 * @class CreateVisitorMessage
 * @ingroup message
 *
 * @brief Message for creating a visitor.
 */
class CreateVisitorMessage : public DocumentMessage {
private:
    string _libName;
    string _instanceId;
    string _controlDestination;
    string _dataDestination;
    string _bucketSpace;
    string _docSelection;
    uint32_t _maxPendingReplyCount;
    std::vector<document::BucketId> _buckets;
    Timestamp _fromTime;
    Timestamp _toTime;
    bool   _visitRemoves;
    string _fieldSet;
    bool _visitInconsistentBuckets;
    vdslib::Parameters _params;
    uint32_t _version;
    document::OrderingSpecification::Order _ordering;
    uint32_t _maxBucketsPerVisitor;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    typedef std::unique_ptr<CreateVisitorMessage> UP;

    CreateVisitorMessage(); // must be deserialized into
    CreateVisitorMessage(const string& libraryName,
                         const string& instanceId,
                         const string& controlDestination,
                         const string& dataDestination);
    ~CreateVisitorMessage();

    const string& getLibraryName() const { return _libName; }
    void setLibraryName(const string& value) { _libName = value; }

    const string& getInstanceId() const { return _instanceId; }
    void setInstanceId(const string& value) { _instanceId = value; }

    const string& getDocumentSelection() const { return _docSelection; }
    void setDocumentSelection(const string& value) { _docSelection = value; }

    const string& getControlDestination() const { return _controlDestination; }
    void setControlDestination(const string& value) { _controlDestination = value; };

    const string& getDataDestination() const { return _dataDestination; }
    void setDataDestination(const string& value) { _dataDestination = value; }

    const string& getBucketSpace() const { return _bucketSpace; }
    void setBucketSpace(const string& value) { _bucketSpace = value; }

    const vdslib::Parameters& getParameters() const { return _params; }
    vdslib::Parameters& getParameters() { return _params; }
    void setParameters(const vdslib::Parameters& params) { _params = params; }

    uint32_t getMaximumPendingReplyCount() const { return _maxPendingReplyCount; }
    void setMaximumPendingReplyCount(uint32_t count) { _maxPendingReplyCount = count; }

    const std::vector<document::BucketId>& getBuckets() const { return _buckets; }
    std::vector<document::BucketId>& getBuckets() { return _buckets; }

    const document::BucketId getBucketId() const { return *_buckets.begin(); }

    bool visitRemoves() const { return _visitRemoves; }
    void setVisitRemoves(bool val) { _visitRemoves = val; }

    void setVisitHeadersOnly(bool visitHeadersOnly_) {
        _fieldSet = (visitHeadersOnly_ ? "[header]" : "[all]");
    }

    bool visitHeadersOnly() const {
        return (_fieldSet == "[header]");
    }

    const string & getFieldSet() const { return _fieldSet; }
    void setFieldSet(const vespalib::stringref & fieldSet) { _fieldSet = fieldSet; }

    bool visitInconsistentBuckets() const { return _visitInconsistentBuckets; }
    void setVisitInconsistentBuckets(bool val) { _visitInconsistentBuckets = val; }

    Timestamp getFromTimestamp() const { return _fromTime; };
    void setFromTimestamp(Timestamp from) { _fromTime = from; };

    Timestamp getToTimestamp() const { return _toTime; };
    void setToTimestamp(Timestamp to) { _toTime = to; };

    document::OrderingSpecification::Order getVisitorOrdering() const { return _ordering; }
    void setVisitorOrdering(document::OrderingSpecification::Order ordering) { _ordering = ordering; }

    uint32_t getMaxBucketsPerVisitor() const { return _maxBucketsPerVisitor; }
    void setMaxBucketsPerVisitor(uint32_t max) { _maxBucketsPerVisitor = max; }

    uint32_t getType() const override;

    void setVisitorDispatcherVersion(uint32_t version) { _version = version; };
    uint32_t getVisitorDispatcherVersion() const { return _version; };

    string toString() const override { return "createvisitormessage"; }
};

/**
 * @class DestroyVisitorMessage
 * @ingroup message
 *
 * @brief Message for removing a visitor.
 */
class DestroyVisitorMessage : public DocumentMessage {
private:
    string _instanceId;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    typedef std::unique_ptr<DestroyVisitorMessage> UP;

    DestroyVisitorMessage(); // must be deserialized into
    DestroyVisitorMessage(const string &instanceId);
    ~DestroyVisitorMessage();

    const string& getInstanceId() const { return _instanceId; }
    void setInstanceId(const string& id) { _instanceId = id; }

    uint32_t getType() const override;
    string toString() const override { return "destroyvisitormessage"; }
};

/**
 * Superclass for all commands sent from VisitorManager to a Visitor
 * client.
 */
class VisitorMessage : public DocumentMessage {
protected:
    VisitorMessage() { }
};

/**
 * Superclass for all commands sent from VisitorManager to a Visitor
 * client.
 */
class VisitorReply : public WriteDocumentReply {
public:
    VisitorReply(uint32_t type);
};

class CreateVisitorReply : public DocumentReply {
private:
    document::BucketId _lastBucket;
    vdslib::VisitorStatistics _visitorStatistics;

public:
    CreateVisitorReply(uint32_t type);

    void setLastBucket(document::BucketId lastBucket) { _lastBucket = lastBucket; }

    document::BucketId getLastBucket() const { return _lastBucket; }

    const vdslib::VisitorStatistics& getVisitorStatistics() const { return _visitorStatistics; }
    void setVisitorStatistics(const vdslib::VisitorStatistics& stats) { _visitorStatistics = stats; }

    string toString() const override { return "createvisitorreply"; }
};

/**
 * @class VisitorInfoMessage
 * @ingroup message
 *
 * @brief Sends status information of an ongoing visitor.
 *
 *  - Notification when individual buckets have been completely visited.
 */
class VisitorInfoMessage : public VisitorMessage {
private:
    std::vector<document::BucketId> _finishedBuckets;
    string                     _errorMessage;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    typedef std::unique_ptr<VisitorInfoMessage> UP;

    VisitorInfoMessage();
    ~VisitorInfoMessage();

    std::vector<document::BucketId>& getFinishedBuckets() { return _finishedBuckets; }
    const std::vector<document::BucketId>& getFinishedBuckets() const { return _finishedBuckets; }

    const string& getErrorMessage() const { return _errorMessage; }
    void setErrorMessage(const string& errorMessage) { _errorMessage = errorMessage; };

    uint32_t getType() const override;
    string toString() const override { return "visitorinfomessage"; }
};

/**
 * @class MapVisitorMessage
 * @ingroup message
 *
 * @brief Sends a docblock to a visitor.
 */
class MapVisitorMessage : public VisitorMessage {
private:
    vdslib::Parameters _data;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    typedef std::unique_ptr<MapVisitorMessage> UP;

    MapVisitorMessage();

    vdslib::Parameters& getData() { return _data; };
    const vdslib::Parameters& getData() const { return _data; };

    uint32_t getApproxSize() const override;
    uint32_t getType() const override;
    string toString() const override { return "mapvisitormessage"; }
};

/**
 * @class DocumentListMessage
 * @ingroup message
 */
class DocumentListMessage : public VisitorMessage {
public:
    typedef std::unique_ptr<DocumentListMessage> UP;

    class Entry {
    public:
        Entry();
        Entry(int64_t timestamp, document::Document::SP doc, bool removeEntry);
        Entry(const Entry& other);
        Entry(const document::DocumentTypeRepo &repo, document::ByteBuffer& buf);

        int64_t getTimestamp() { return _timestamp; }
        const document::Document::SP& getDocument() { return _document; }
        bool isRemoveEntry() { return _removeEntry; }

        void serialize(document::ByteBuffer& buf) const;
        uint32_t getSerializedSize() const;
    private:
        int64_t _timestamp;
        document::Document::SP _document;
        bool _removeEntry;
    };

private:
    document::BucketId _bucketId;
    std::vector<Entry> _documents;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    DocumentListMessage();
    DocumentListMessage(document::BucketId bid);

    const document::BucketId& getBucketId() const { return _bucketId; };
    void setBucketId(const document::BucketId& id) { _bucketId = id; };

    std::vector<Entry>& getDocuments() { return _documents; };
    const std::vector<Entry>& getDocuments() const { return _documents; };

    uint32_t getType() const override;
    string toString() const override { return "documentlistmessage"; }
};

}

