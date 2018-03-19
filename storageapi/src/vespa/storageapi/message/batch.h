// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/messageapi/bucketinfocommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>

namespace storage::api {

/**
 * @class BatchPutRemoveCommand
 * @ingroup message
 *
 * @brief Sends a batch of puts and removes
 */
class BatchPutRemoveCommand : public BucketInfoCommand {
public:
    class Operation {
    public:
        enum Type {
            REMOVE, // Removes a document
            HEADERUPDATE, // Updates the header of a document, if it already exists.
            PUT // Inserts a new document.
        };

        Operation(uint64_t ts, Type type);
        virtual ~Operation() {};

        uint64_t timestamp;
        Type type;

        virtual const document::DocumentId& getDocumentId() const = 0;
    };

    explicit BatchPutRemoveCommand(const document::Bucket &bucket);

    class PutOperation : public Operation {
    public:
        PutOperation(document::Document::SP document, uint64_t timestamp);

        document::Document::SP document;

        const document::DocumentId& getDocumentId() const override {
            return document->getId();
        }
    };

    class HeaderUpdateOperation : public Operation {
    public:
        HeaderUpdateOperation(document::Document::SP document, uint64_t newTimestamp, uint64_t timestampToUpdate);

        document::Document::SP document;
        uint64_t timestampToUpdate;

        const document::DocumentId& getDocumentId() const override {
            return document->getId();
        }
    };

    class RemoveOperation : public Operation {
    public:
        RemoveOperation(const document::DocumentId& docId, uint64_t timestamp);

        document::DocumentId documentId;

        const document::DocumentId& getDocumentId() const override {
            return documentId;
        }
    };

    /**
       Adds a PUT operation to be performed.
    */
    void addPut(document::Document::SP document, uint64_t timestamp);

    /**
       Adds a PUT operation to be performed.
    */
    void addHeaderUpdate(document::Document::SP document, uint64_t newTimestamp, uint64_t timestampToUpdate);

    /**
       Adds a REMOVE operation to be performed.
    */
    void addRemove(const document::DocumentId& docId, uint64_t timestamp);

    /**
     * Adds an operation to be performed. Optionally deep-clones the
     * operation's document.
     */
    void addOperation(const Operation& op, bool cloneDocument = false);

    /**
       Returns the number of operations in this batch.
    */
    uint32_t getOperationCount() const { return _operations.size(); }

    /**
       Returns the nth operation in this batch.
    */
    const Operation& getOperation(uint32_t index) const { return *_operations[index]; }

    /**
       Returns the nth operation in this batch.
    */
    Operation& getOperation(uint32_t index) { return *_operations[index]; }

    /**
       Returns an approximate size of this message.
    */
    uint32_t getMemoryFootprint() const override { return _approxSize + 20; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(BatchPutRemoveCommand, onBatchPutRemove)

private:
    std::vector<std::unique_ptr<Operation> > _operations;
    uint32_t _approxSize;
};

/**
 * @class BatchPutRemoveReply
 * @ingroup message
 *
 * @brief Confirm that a given docoperations have been received.
 */
class BatchPutRemoveReply : public BucketInfoReply {
private:
    std::vector<document::DocumentId> _documentsNotFound;

public:
    explicit BatchPutRemoveReply(const BatchPutRemoveCommand&);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    const std::vector<document::DocumentId>& getDocumentsNotFound() const { return _documentsNotFound; }
    std::vector<document::DocumentId>& getDocumentsNotFound() { return _documentsNotFound; }

    DECLARE_STORAGEREPLY(BatchPutRemoveReply, onBatchPutRemoveReply)
};

class BatchDocumentUpdateCommand : public StorageCommand
{
public:
    typedef std::vector<document::DocumentUpdate::SP > UpdateList;

    /**
       Creates a batch update message containing the given updates.
    */
    BatchDocumentUpdateCommand(const UpdateList& updates);

    /**
       @return Returns a list of the updates to be performed.
    */
    const UpdateList& getUpdates() const { return _updates; };
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
       Returns a bucket suitable for routing this message.
    */
    document::Bucket getBucket() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    DECLARE_STORAGECOMMAND(BatchDocumentUpdateCommand, onBatchDocumentUpdate)

private:
    UpdateList _updates;
    document::Bucket _bucket;
};

/**
 * @class BatchDocumentUpdateReply
 * @ingroup message
 *
 * @brief Confirm that a given docoperations have been received.
 */
class BatchDocumentUpdateReply : public StorageReply {
    // 1-1 mapping of found/not found state for documents
    std::vector<bool> _documentsNotFound;
public:
    explicit BatchDocumentUpdateReply(const BatchDocumentUpdateCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    const std::vector<bool>& getDocumentsNotFound() const { return _documentsNotFound; }
    std::vector<bool>& getDocumentsNotFound() { return _documentsNotFound; }

    DECLARE_STORAGEREPLY(BatchDocumentUpdateReply, onBatchDocumentUpdateReply)
};

}
