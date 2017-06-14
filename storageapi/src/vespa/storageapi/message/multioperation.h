// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/storageapi/messageapi/bucketinfocommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>
#include <vespa/storageapi/defs.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vdslib/container/writabledocumentlist.h>

namespace storage {
namespace api {

/**
 * @class MultiOperationCommand
 * @ingroup message
 *
 * @brief Sends a documentlist
 */
class MultiOperationCommand : public BucketInfoCommand {
private:
    std::vector<char> _buffer; // Used to hold data refered to by document list
                               // if message is to own its data.
    vdslib::WritableDocumentList _operations;
    bool _keepTimeStamps;

public:
    explicit MultiOperationCommand(const document::DocumentTypeRepo::SP &repo,
                                   const document::BucketId& id,
                                   int bufferSize,
                                   bool keepTimeStamps = false);
    explicit MultiOperationCommand(const document::DocumentTypeRepo::SP &repo,
                                   const document::BucketId& id,
                                   const std::vector<char>& buffer,
                                   bool keepTimeStamps = false);
    explicit MultiOperationCommand(const MultiOperationCommand& template_);
    ~MultiOperationCommand();

    std::vector<char>& getBuffer() { return _buffer; };
    const std::vector<char>& getBuffer() const { return _buffer; };

    vdslib::WritableDocumentList& getOperations()
        { assert(_operations.getBufferSize() > 0); return _operations; }
    const vdslib::WritableDocumentList& getOperations() const
        { assert(_operations.getBufferSize() > 0); return _operations; }

    void setOperations(vdslib::WritableDocumentList& operations) {
        _buffer.clear();
        _operations = operations;
    }

    uint32_t getMemoryFootprint() const override {
        return _buffer.size() + 20;
    }

    bool keepTimeStamps() const { return _keepTimeStamps; }
    void keepTimeStamps(bool keepTime) { _keepTimeStamps = keepTime; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(MultiOperationCommand, onMultiOperation)
};

/**
 * @class MultiOperationReply
 * @ingroup message
 *
 * @brief Confirm that a given docoperations have been received.
 */
class MultiOperationReply : public BucketInfoReply {
private:
    // No need to serialize this, as it's only used internally in the distributor.
    uint64_t _highestModificationTimestamp;

public:
    explicit MultiOperationReply(const MultiOperationCommand&);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void setHighestModificationTimestamp(uint64_t highestModificationTimestamp) {
        _highestModificationTimestamp = highestModificationTimestamp;
    }
    uint64_t getHighestModificationTimestamp() const { return _highestModificationTimestamp; }

    DECLARE_STORAGEREPLY(MultiOperationReply, onMultiOperationReply)
};

} // api
} // storage

