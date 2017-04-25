// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storageapi/defs.h>
#include <vespa/vdslib/container/documentlist.h>

namespace storage {
namespace api {

/**
 * @class DocBlockCommand
 * @ingroup message
 *
 * @brief Sends a docblock to a visitor or subscriber.
 */
class DocBlockCommand : public StorageCommand {
    document::BucketId _bucketId;
    vdslib::DocumentList _docBlock;
    std::shared_ptr<void> _buffer; // Owns data in docblock
    bool _keepTimeStamps; // Used for recovery/synchronization where we want to
                          // keep the timestamps of the origin.

public:
    DocBlockCommand(const document::BucketId& bucketId,
                    const vdslib::DocumentList& block,
                    const std::shared_ptr<void>& buffer);

    vdslib::DocumentList& getDocumentBlock()
        { assert(_docBlock.getBufferSize() > 0); return _docBlock; }
    const vdslib::DocumentList& getDocumentBlock() const
        { assert(_docBlock.getBufferSize() > 0); return _docBlock; }
    void setDocumentBlock(vdslib::DocumentList& block) { _docBlock = block; }

    document::BucketId getBucketId() const override { return _bucketId; }
    bool hasSingleBucketId() const override { return true; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool keepTimeStamps() const { return _keepTimeStamps; }
    void keepTimeStamps(bool keepTime) { _keepTimeStamps = keepTime; }

    DECLARE_STORAGECOMMAND(DocBlockCommand, onDocBlock)
};

/**
 * @class DocBlockReply
 * @ingroup message
 *
 * @brief Confirm that a given docblock have been received.
 */
class DocBlockReply : public StorageReply {
public:
    explicit DocBlockReply(const DocBlockCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(DocBlockReply, onDocBlockReply)
};

/**
 * @class MapStorageCommand
 * @ingroup message
 *
 * @brief Sends a map of data to a visitor.
 *
 * This is a generic way to transfer data to the visitor data handler.
 * It is for instance used when doing a specialized visitor to gather statistics
 * on usage of document types and namespaces.
 */
class MapVisitorCommand : public StorageCommand {
    vdslib::Parameters _statistics;
public:
    MapVisitorCommand();
    vdslib::Parameters& getData() { return _statistics; };
    const vdslib::Parameters& getData() const { return _statistics; };
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(MapVisitorCommand, onMapVisitor)
};

/**
 * @class MapStorageReply
 * @ingroup message
 *
 * @brief Confirm that a given map visitor command has been received.
 */
class MapVisitorReply : public StorageReply {
public:
    explicit MapVisitorReply(const MapVisitorCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(MapVisitorReply, onMapVisitorReply)
};

/**
 * @class DocumentListCommand
 * @ingroup message
 *
 * @brief Sends a list of documents to the visitor data handler.
 *
 * This is used in synchronization in order to transfer minimal amount of data
 * to the synchronization agent.
 */
class DocumentListCommand : public StorageCommand {
public:
    struct Entry {
        document::Document::SP _doc;
        int64_t _lastModified;
        bool _removeEntry;

        Entry() : _doc(), _lastModified(0), _removeEntry(false) {}
        Entry(const document::Document::SP& doc, int64_t lastModified,
              bool removeEntry)
            : _doc(doc),
              _lastModified(lastModified),
              _removeEntry(removeEntry)
        { }
    };

private:
    document::BucketId _bucketId;
    std::vector<Entry> _documents;
public:
    DocumentListCommand(const document::BucketId& bid);
    const document::BucketId& getBucketId() { return _bucketId; }
    std::vector<Entry>& getDocuments() { return _documents; }
    const std::vector<Entry>& getDocuments() const { return _documents; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(DocumentListCommand, onDocumentList)
};

std::ostream& operator<<(std::ostream& out, const DocumentListCommand::Entry& e);

/**
 * @class DocumentListReply
 * @ingroup message
 *
 * @brief Confirm that a given visitorstatisticscommand has been received.
 */
class DocumentListReply : public StorageReply {
public:
    explicit DocumentListReply(const DocumentListCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(DocumentListReply, onDocumentListReply)
};

/**
 * @class EmptyBucketsCommand
 * @ingroup message
 *
 * @brief Sends a vector of bucket ids to a visitor.
 *
 * This message is used in synchronization to tell the synchronization client
 * that a bucket contains no data at all. This is needed to let the follower be
 * able to delete documents from these buckets, as they would otherwise be
 * ignored by the synch agent.
 */
class EmptyBucketsCommand : public StorageCommand {
    std::vector<document::BucketId> _buckets;
public:
    EmptyBucketsCommand(const std::vector<document::BucketId>&);
    const std::vector<document::BucketId>& getBuckets() const { return _buckets; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(EmptyBucketsCommand, onEmptyBuckets)
};

/**
 * @class EmptyBucketsReply
 * @ingroup message
 *
 * @brief Confirm that a given emptybucketscommad has been received.
 */
class EmptyBucketsReply : public StorageReply {
public:
    explicit EmptyBucketsReply(const EmptyBucketsCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(EmptyBucketsReply, onEmptyBucketsReply)
};

} // api
} // storage
