// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "batch.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <ostream>

using namespace storage::api;
using document::BucketSpace;

IMPLEMENT_COMMAND(BatchPutRemoveCommand, BatchPutRemoveReply)
IMPLEMENT_REPLY(BatchPutRemoveReply)
IMPLEMENT_COMMAND(BatchDocumentUpdateCommand, BatchDocumentUpdateReply)
IMPLEMENT_REPLY(BatchDocumentUpdateReply)


BatchPutRemoveCommand::Operation::Operation(uint64_t ts, Type tp)
    : timestamp(ts),
      type(tp)
{
}

BatchPutRemoveCommand::PutOperation::PutOperation(document::Document::SP doc, uint64_t ts)
    : Operation(ts, PUT),
      document(doc)
{
}

BatchPutRemoveCommand::HeaderUpdateOperation::HeaderUpdateOperation(document::Document::SP doc, uint64_t newTimestamp, uint64_t timestampToUpdate_)
    : Operation(newTimestamp, HEADERUPDATE),
      document(doc),
      timestampToUpdate(timestampToUpdate_)
{
}

BatchPutRemoveCommand::RemoveOperation::RemoveOperation(const document::DocumentId& docId, uint64_t ts)
    : Operation(ts, REMOVE),
      documentId(docId)
{
}

BatchPutRemoveCommand::BatchPutRemoveCommand(const document::Bucket &bucket)
    : BucketInfoCommand(MessageType::BATCHPUTREMOVE, bucket),
      _approxSize(0)
{
}

void
BatchPutRemoveCommand::addPut(document::Document::SP document, uint64_t ts)
{
    _operations.push_back(std::unique_ptr<Operation>(new PutOperation(document, ts)));
    _approxSize += document->serialize()->getLength();
}

void
BatchPutRemoveCommand::addHeaderUpdate(document::Document::SP document, uint64_t ts, uint64_t timestampToUpdate)
{
    _operations.push_back(std::unique_ptr<Operation>(new HeaderUpdateOperation(document, ts, timestampToUpdate)));
    _approxSize += document->serialize()->getLength();
}

void
BatchPutRemoveCommand::addRemove(const document::DocumentId& docId, uint64_t ts)
{
    _operations.push_back(std::unique_ptr<Operation>(new RemoveOperation(docId, ts)));
    _approxSize += docId.toString().length();
}

void
BatchPutRemoveCommand::addOperation(const Operation& op, bool cloneDocument)
{
    switch (op.type) {
    case Operation::PUT:
    {
        document::Document::SP doc;
        if (!cloneDocument) {
            doc = static_cast<const PutOperation&>(op).document;
        } else {
            doc.reset(static_cast<const PutOperation&>(op).document->clone());
        }
        addPut(doc, op.timestamp);
        break;
    }
    case Operation::REMOVE:
        addRemove(static_cast<const RemoveOperation&>(op).documentId, op.timestamp);
        break;
    case Operation::HEADERUPDATE:
    {
        const HeaderUpdateOperation& hup = static_cast<const HeaderUpdateOperation&>(op);
        document::Document::SP doc;
        if (!cloneDocument) {
            doc = hup.document;
        } else {
            doc.reset(hup.document->clone());
        }
        addHeaderUpdate(doc, op.timestamp, hup.timestampToUpdate);
        break;
    }
    }
}

void
BatchPutRemoveCommand::print(std::ostream& out, bool verbose,
               const std::string& indent) const {
    out << "BatchPutRemove(" << getBucketId() << ", " << _operations.size() << " operations)";

    if (verbose) {
        out << " : ";
        BucketInfoCommand::print(out, verbose, indent);
    }
}

BatchPutRemoveReply::BatchPutRemoveReply(const BatchPutRemoveCommand& cmd)
    : BucketInfoReply(cmd)
{
}

void
BatchPutRemoveReply::print(std::ostream& out, bool verbose,
               const std::string& indent) const {
    out << "BatchPutRemoveReply(";
    out << _documentsNotFound.size() << " documents not found)";

    if (verbose) {
        out << " {";
        for (std::vector<document::DocumentId>::const_iterator it =
                 _documentsNotFound.begin();
             it != _documentsNotFound.end(); ++it)
        {
            out << "\n" << indent << "  " << (*it);
        }
        out << "\n" << indent << "} : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

BatchDocumentUpdateCommand::BatchDocumentUpdateCommand(const UpdateList& updates)
    : StorageCommand(MessageType::BATCHDOCUMENTUPDATE),
      _updates(updates),
      _bucket(BucketSpace::placeHolder(), document::BucketId())
{
    document::BucketIdFactory factory;
    _bucket = document::Bucket(BucketSpace::placeHolder(), factory.getBucketId(updates[0]->getId()));
}

void
BatchDocumentUpdateCommand::print(std::ostream& out, bool verbose,
               const std::string& indent) const {
    out << "BatchDocumentUpdate(" << _updates.size() << " operations)";

    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

BatchDocumentUpdateReply::BatchDocumentUpdateReply(const BatchDocumentUpdateCommand& cmd)
    : StorageReply(cmd),
      _documentsNotFound()
{
}

void
BatchDocumentUpdateReply::print(std::ostream& out, bool verbose,
               const std::string& indent) const {
    out << "BatchDocumentUpdateReply("
        << std::count(_documentsNotFound.begin(), _documentsNotFound.end(), true)
        << " not found)";

    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}
